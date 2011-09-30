package com.intellij.flex.uiDesigner.mxml;

import com.intellij.flex.uiDesigner.InvalidPropertyException;
import com.intellij.flex.uiDesigner.ProblemsHolder;
import com.intellij.flex.uiDesigner.io.ByteRange;
import com.intellij.flex.uiDesigner.io.PrimitiveAmfOutputStream;
import com.intellij.lang.javascript.flex.AnnotationBackedDescriptor;
import com.intellij.lang.javascript.psi.JSCommonTypeNames;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlTag;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class InjectedASWriter implements ValueReferenceResolver {
  private final THashMap<String, MxmlObjectReference> idReferenceMap = new THashMap<String, MxmlObjectReference>();
  private final THashMap<JSVariable, VariableReference> variableReferenceMap = new THashMap<JSVariable, VariableReference>();

  private final List<Binding> bindingItems = new ArrayList<Binding>();
  private final BaseWriter writer;
  MxmlObjectReference lastMxmlObjectReference;

  private ProblemsHolder problemsHolder;

  private ByteRange declarationsRange;

  final static ValueWriter IGNORE = new ValueWriter() {
    @Override
    public PropertyProcessor.PropertyKind write(AnnotationBackedDescriptor descriptor, XmlElementValueProvider valueProvider,
                                                PrimitiveAmfOutputStream out, BaseWriter writer,
                                                boolean isStyle, Context parentContext) {
      throw new UnsupportedOperationException();
    }
  };

  public InjectedASWriter(BaseWriter writer) {
    this.writer = writer;
  }

  void setProblemsHolder(@Nullable ProblemsHolder problemsHolder) {
    this.problemsHolder = problemsHolder;
  }

  @NotNull
  @Override
  public VariableReference getValueReference(JSVariable jsVariable) {
    return variableReferenceMap.get(jsVariable);
  }

  @Override
  public VariableReference getNullableValueReference(JSVariable jsVariable) {
    return variableReferenceMap.get(jsVariable);
  }

  @NotNull
  @Override
  public MxmlObjectReference getValueReference(String id) throws InvalidPropertyException {
    return idReferenceMap.get(id);
  }

  public ValueWriter processProperty(XmlElementValueProvider valueProvider, String name, @Nullable String type, boolean isStyle,
                                     @Nullable Context context) throws InvalidPropertyException {
    PsiElement host = valueProvider.getInjectedHost();
    if (host == null) {
      return null;
    }

    final InjectedPsiVisitor visitor;
    if (JSCommonTypeNames.ARRAY_CLASS_NAME.equals(type)) {
      visitor = new InjectedPsiVisitor(host, JSCommonTypeNames.ARRAY_CLASS_NAME, problemsHolder);
    }
    else {
      visitor = new InjectedPsiVisitor(host, type, problemsHolder);
    }

    InjectedLanguageUtil.enumerate(host, visitor);

    //noinspection ThrowableResultOfMethodCallIgnored
    if (visitor.getInvalidPropertyException() != null) {
      throw visitor.getInvalidPropertyException();
    }
    else {
      final Binding binding = visitor.getBinding();
      if (binding != null) {
        if (binding instanceof VariableBinding) {
          JSVariable variable = ((VariableBinding)binding).variable;
          VariableReference variableReference = variableReferenceMap.get(variable);
          if (variableReference == null) {
            variableReferenceMap.put(variable, new VariableReference(variable));
          }
          else {
            variableReference.markAsMultipleReferred();
          }
        }

        if (lastMxmlObjectReference == null) {
          lastMxmlObjectReference = new MxmlObjectReference(writer.getObjectOrFactoryId(context));
        }

        binding.setTarget(lastMxmlObjectReference, writer.getNameReference(name), isStyle);
        bindingItems.add(binding);
        return IGNORE;
      }
      else {
        return visitor.getValueWriter();
      }
    }
  }

  public void readDeclarations(MxmlWriter mxmlWriter, XmlTag tag) {
    declarationsRange = writer.getBlockOut().startRange();
    mxmlWriter.processDeclarations(tag);
    writer.getBlockOut().endRange(declarationsRange);
  }

  public void write() {
    writeDeclarations();
    writeBinding(writer.getOut());

    reset();
  }

  private void writeDeclarations() {
    if (declarationsRange == null) {
      writer.getOut().writeShort(0);
    }
    else {
      writer.addMarker(declarationsRange);
      declarationsRange = null;
    }
  }

  private void writeBinding(PrimitiveAmfOutputStream out) {
    if (bindingItems.isEmpty()) {
      out.writeShort(0);
      return;
    }
    
    final int bindingSizePosition = out.allocateShort();
    int size = bindingItems.size();
    
    for (Binding binding : bindingItems) {
      int beforePosition = out.size();
      try {
        binding.write(out, writer, this);
        continue;
      }
      catch (UnsupportedOperationException e) {
        MxmlWriter.LOG.warn("unsupported injected AS: " + e.getMessage());
      }
      catch (Throwable e) {
        problemsHolder.add(e);
      }
      
      size--;
      out.getByteOut().setPosition(beforePosition);
    }

    out.putShort(size, bindingSizePosition);
  }

  public void reset() {
    bindingItems.clear();

    idReferenceMap.clear();
    variableReferenceMap.clear();

    lastMxmlObjectReference = null;
  }

  void processObjectWithExplicitId(String explicitId, Context context) {
    lastMxmlObjectReference = new MxmlObjectReference(writer.getObjectOrFactoryId(context));
    idReferenceMap.put(explicitId, lastMxmlObjectReference);
  }

  void putMxmlObjectReference(@NotNull String explicitId, Context context) {
    idReferenceMap.put(explicitId, new MxmlObjectReference(writer.getObjectOrFactoryId(context)));
  }

  void setDeferredReferenceForObjectWithExplicitIdOrBinding(
    StaticInstanceReferenceInDeferredParentInstance staticReferenceInDeferredParentInstance, int referenceInstance) {
    assert lastMxmlObjectReference.id == referenceInstance;
    lastMxmlObjectReference.staticReferenceInDeferredParentInstance = staticReferenceInDeferredParentInstance;

    lastMxmlObjectReference = null;
  }
}