package com.google.zxing.qrcode.encoder;


public class GridChart {
  private int x;
  private int y;

  GridChart() {
    this.x = 0;
    this.y = 0;
  }

  GridChart(int px, int py) {
    this.x = px;
    this.y = py;
  }

  public void setPosition(int px, int py) {
    x = px;
    y = py;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }
}
