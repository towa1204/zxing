package com.google.zxing.qrcode.decoder;

import org.junit.Test;

import java.util.ArrayList;

public class DecoderTest {

  @Test
  public void testNkDivision() {
    int n = 2442;
    int k = 856;

    ArrayList<ArrayList<Integer>> param = Decoder.nkDivision(n, k);

    for (int i = 0; i < param.get(0).size(); i++) {
      int cnt = param.get(0).get(i);
      int np = param.get(1).get(i);
      int kp = param.get(2).get(i);
      System.out.println(cnt + "×" + "(" + np + "," + kp + ")");
    }

  }

}
