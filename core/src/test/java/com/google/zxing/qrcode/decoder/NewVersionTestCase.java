package com.google.zxing.qrcode.decoder;

import org.junit.Assert;
import org.junit.Test;

public final class NewVersionTestCase extends Assert{

  @Test
  public void checkVersion1() {
    NewVersion version = new NewVersion(4, ErrorCorrectionLevel.M, 60);
    int totalCodewords = NewVersion.getTotalCodewords(4);
    int totalDataCodewords = version.getTotalDataCodewords();
    NewVersion.NewECBlocks ecBlocks = version.getECBlocks();
    int numBlocks = ecBlocks.getNumBlocks();
    assertEquals(100, totalCodewords);
    assertEquals(60, totalDataCodewords);
    assertEquals(2, numBlocks);
    System.out.println("totalN = " + totalCodewords);
    System.out.println("totalK = " + totalDataCodewords);
    System.out.println("numRSBlocks = " + numBlocks);
    for(NewVersion.NewECB ecb : ecBlocks.getECBlocks()) {
      System.out.println(ecb.getCount() + "×" +
    "(" + ecb.getCodewords() + "," + ecb.getDataCodewords() + ")");
    }
    System.out.println();
  }

  @Test
  public void checkVersion2() {
    int versionNumber = 10;
    int kp = 60;
    NewVersion version = new NewVersion(versionNumber, ErrorCorrectionLevel.L, kp);
    int totalCodewords = NewVersion.getTotalCodewords(versionNumber);
    int totalDataCodewords = version.getTotalDataCodewords();
    NewVersion.NewECBlocks ecBlocks = version.getECBlocks();
    int numBlocks = ecBlocks.getNumBlocks();
    assertEquals(346, totalCodewords);
    assertEquals(60, totalDataCodewords);
    assertEquals(5, numBlocks);
    System.out.println("totalN = " + totalCodewords);
    System.out.println("totalK = " + totalDataCodewords);
    System.out.println("numRSBlocks = " + numBlocks);
    for(NewVersion.NewECB ecb : ecBlocks.getECBlocks()) {
      System.out.println(ecb.getCount() + "×" +
    "(" + ecb.getCodewords() + "," + ecb.getDataCodewords() + ")");
    }
    System.out.println();
  }

  @Test
  public void checkVersion3() {
    int versionNumber = 15;
    int kp = 60;
    NewVersion version = new NewVersion(versionNumber, ErrorCorrectionLevel.L, kp);
    int totalCodewords = NewVersion.getTotalCodewords(versionNumber);
    int totalDataCodewords = version.getTotalDataCodewords();
    NewVersion.NewECBlocks ecBlocks = version.getECBlocks();
    int numBlocks = ecBlocks.getNumBlocks();
    assertEquals(655, totalCodewords);
    assertEquals(60, totalDataCodewords);
    assertEquals(5, numBlocks);
    System.out.println("totalN = " + totalCodewords);
    System.out.println("totalK = " + totalDataCodewords);
    System.out.println("numRSBlocks = " + numBlocks);
    for(NewVersion.NewECB ecb : ecBlocks.getECBlocks()) {
      System.out.println(ecb.getCount() + "×" +
    "(" + ecb.getCodewords() + "," + ecb.getDataCodewords() + ")");
    }
    System.out.println();
  }
}
