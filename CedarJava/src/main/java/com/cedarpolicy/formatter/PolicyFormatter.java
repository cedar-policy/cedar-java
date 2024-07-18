package com.cedarpolicy.formatter;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;

public final class PolicyFormatter {

  static {
    LibraryLoader.loadLibrary();
  }

  private PolicyFormatter() {
  }

  public static native String policiesStrToPretty(String policies)
      throws InternalException, NullPointerException;
}
