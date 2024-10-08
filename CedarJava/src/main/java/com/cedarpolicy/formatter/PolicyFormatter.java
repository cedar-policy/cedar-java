package com.cedarpolicy.formatter;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.formatter.Config;

public final class PolicyFormatter {

  static {
    LibraryLoader.loadLibrary();
  }

  private PolicyFormatter() {
  }

  public static native String policiesStrToPretty(String policies)
      throws InternalException, NullPointerException;

  public static native String policiesStrToPrettyWithConfig(String policies, Config config)
      throws InternalException, NullPointerException;
}
