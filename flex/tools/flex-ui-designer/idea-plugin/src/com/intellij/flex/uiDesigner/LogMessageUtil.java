package com.intellij.flex.uiDesigner;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.flex.uiDesigner.libraries.InitException;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.Nullable;

public final class LogMessageUtil {
  public static StringBuilder appendLineNumber(StringBuilder builder, ProblemDescriptor problemDescriptor) {
    return builder.append(" (line: ").append(problemDescriptor.getLineNumber()).append(')');
  }
  
  public static String createMxmlTitle(@Nullable VirtualFile file) {
    return file == null
           ? FlexUIDesignerBundle.message("problem.opening.mxml.document")
           : FlexUIDesignerBundle.message("problem.opening.0", file.getName());
  }

  public static String createBaloonText(@Nullable VirtualFile file) {
    return file == null
           ? FlexUIDesignerBundle.message("problem.opening.mxml.document")
           : FlexUIDesignerBundle.message("problem.opening.balloon.text", file.getName());
  }

  @Nullable
  public static Attachment createAttachment(@Nullable VirtualFile file) {
    return file == null ? null : new Attachment(file);
  }

  public static IdeaLoggingEvent createEvent(String userMessage, Throwable e, ProblemDescriptor problemDescriptor) {
    final String message = appendLineNumber(new StringBuilder(userMessage), problemDescriptor).toString();
    return LogMessageEx.createEvent(message, ExceptionUtil.getThrowableText(e), createMxmlTitle(problemDescriptor.getFile()), message,
                                    createAttachment(problemDescriptor.getFile()));
  }

  public static IdeaLoggingEvent createEvent(String userMessage, String technicalMessage, @Nullable VirtualFile file) {
    return LogMessageEx.createEvent(userMessage, technicalMessage, createMxmlTitle(file), createBaloonText(file), createAttachment(file));
  }

  public static IdeaLoggingEvent createEvent(Throwable e, @Nullable VirtualFile file) {
    return createEvent(e.getMessage(), ExceptionUtil.getThrowableText(e), file);
  }

  public static void processInternalError(Throwable e, @Nullable VirtualFile mxmlFile) {
    FlexUIDesignerApplicationManager.LOG.error(createEvent(e, mxmlFile));
  }

  public static void processInternalError(Throwable e) {
    processInternalError(e, null);
  }
}