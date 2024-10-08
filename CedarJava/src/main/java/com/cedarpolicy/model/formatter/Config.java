package com.cedarpolicy.model.formatter;

public class Config {

  private final int lineWidth;
  private final int indentWidth;

  public Config(int lineWidth, int indentWidth) {
    this.lineWidth = lineWidth;
    this.indentWidth = indentWidth;
  }

  @SuppressWarnings("unused")
  public int getLineWidth() {
    return lineWidth;
  }

  @SuppressWarnings("unused")
  public int getIndentWidth() {
    return indentWidth;
  }
}
