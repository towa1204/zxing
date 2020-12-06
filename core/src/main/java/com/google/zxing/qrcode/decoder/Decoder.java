/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.qrcode.decoder;

import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;
import com.google.zxing.qrcode.encoder.Encoder;

import java.util.ArrayList;
import java.util.Map;

/**
 * <p>The main class which implements QR Code decoding -- as opposed to locating and extracting
 * the QR Code from an image.</p>
 *
 * @author Sean Owen
 */
public final class Decoder {

  private final ReedSolomonDecoder rsDecoder;

  public Decoder() {
    rsDecoder = new ReedSolomonDecoder(GenericGF.QR_CODE_FIELD_256);
  }

  public DecoderResult decode(boolean[][] image) throws ChecksumException, FormatException {
    return decode(image, null);
  }

  /**
   * <p>Convenience method that can decode a QR Code represented as a 2D array of booleans.
   * "true" is taken to mean a black module.</p>
   *
   * @param image booleans representing white/black QR Code modules
   * @param hints decoding hints that should be used to influence decoding
   * @return text and bytes encoded within the QR Code
   * @throws FormatException if the QR Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(boolean[][] image, Map<DecodeHintType,?> hints)
      throws ChecksumException, FormatException {
    return decode(BitMatrix.parse(image), hints);
  }

  public DecoderResult decode(BitMatrix bits) throws ChecksumException, FormatException {
    return decode(bits, null);
  }

  /**
   * <p>Decodes a QR Code represented as a {@link BitMatrix}. A 1 or "true" is taken to mean a black module.</p>
   *
   * @param bits booleans representing white/black QR Code modules
   * @param hints decoding hints that should be used to influence decoding
   * @return text and bytes encoded within the QR Code
   * @throws FormatException if the QR Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(BitMatrix bits, Map<DecodeHintType,?> hints)
      throws FormatException, ChecksumException {

    // Construct a parser and read version, error-correction level
    BitMatrixParser parser = new BitMatrixParser(bits);
    FormatException fe = null;
    ChecksumException ce = null;
    try {
      return decode(parser, hints);
    } catch (FormatException e) {
      fe = e;
    } catch (ChecksumException e) {
      ce = e;
    }

    try {

      // Revert the bit matrix
      parser.remask();

      // Will be attempting a mirrored reading of the version and format info.
      parser.setMirror(true);

      // Preemptively read the version.
      parser.readVersion();

      // Preemptively read the format information.
      parser.readFormatInformation();

      /*
       * Since we're here, this means we have successfully detected some kind
       * of version and format information when mirrored. This is a good sign,
       * that the QR code may be mirrored, and we should try once more with a
       * mirrored content.
       */
      // Prepare for a mirrored reading.
      parser.mirror();

      DecoderResult result = decode(parser, hints);

      // Success! Notify the caller that the code was mirrored.
      result.setOther(new QRCodeDecoderMetaData(true));

      return result;

    } catch (FormatException | ChecksumException e) {
      // Throw the exception from the original reading
      if (fe != null) {
        throw fe;
      }
      throw ce; // If fe is null, this can't be
    }
  }

  private DecoderResult decode(BitMatrixParser parser, Map<DecodeHintType,?> hints)
      throws FormatException, ChecksumException {

//    System.out.println("point1");

    Version version = parser.readVersion();

//    System.out.println("point1.1");

    ErrorCorrectionLevel ecLevel = parser.readFormatInformation().getErrorCorrectionLevel();

//    System.out.println("point1.2");

    // Read codewords
    byte[] codewords = parser.readCodewords();

//    System.out.println("point1.3");

    // 型番・誤り訂正レベルごとのパラメータを得る
    int[] commonRSParam = NewVersion.getCommonRSParam(version.getVersionNumber(), ecLevel);
    // 共通RSブロックの位置を得る
    int[] commonRSBlockIndex = Encoder.getCommonRSBlockIndex(commonRSParam[0], commonRSParam[2]);

    byte[] commonRSBlockBytes = new byte[commonRSParam[0]];
    // 共通RSブロックを取り出す
    for (int i = 0; i < commonRSBlockIndex.length; i++) {
      commonRSBlockBytes[i] = codewords[commonRSBlockIndex[i]];
    }
    // 共通RSブロックを誤り訂正
    correctErrors(commonRSBlockBytes, commonRSParam[1]);
//    System.out.println("共通RSブロックの誤り訂正成功");

//    System.out.println("point2");

    // 文字数k'(contentSize)を取得
    int kp = DecodedBitStreamParser.readStrFormatReturnKP(commonRSBlockBytes, version, ecLevel);
//    System.out.println("k' = " + kp);

//    System.out.println("point3");

    NewVersion newVersion = new NewVersion(version.getVersionNumber(), ecLevel, kp);

    // その他のRSブロックをDataBlock[]型として得る
    // Separate into data blocks
    DataBlock[] dataBlocks = DataBlock.getDataBlocks(codewords, newVersion, commonRSBlockIndex);

    // Count total number of data bytes
    int totalBytes = newVersion.getTotalDataCodewords();
    byte[] resultBytes = new byte[totalBytes];
    int resultOffset = 0;

    // Error-correct and copy data blocks together into a stream of bytes
    // resultBytesは情報コードのみ

    // 共通RSブロックを格納
    for (int i = 0; i < commonRSParam[1]; i++) {
      resultBytes[resultOffset++] = commonRSBlockBytes[i];
    }

    // その他のRSブロックを誤り訂正，格納
    for (int i = 1; i < dataBlocks.length; i++) {
      byte[] codewordBytes = dataBlocks[i].getCodewords();
      int numDataCodewords = dataBlocks[i].getNumDataCodewords();
      correctErrors(codewordBytes, numDataCodewords);
      for (int j = 0; j < numDataCodewords; j++) {
        resultBytes[resultOffset++] = codewordBytes[j];
      }
    }

//    System.out.println("point4");
    // Decode the contents of that stream of bytes
    return DecodedBitStreamParser.decode(resultBytes, version, ecLevel, hints);
  }

  /**
   * <p>Given data and error-correction codewords received, possibly corrupted by errors, attempts to
   * correct the errors in-place using Reed-Solomon error correction.</p>
   *
   * @param codewordBytes data and error correction codewords
   * @param numDataCodewords number of codewords that are data bytes
   * @throws ChecksumException if error correction fails
   */
  private void correctErrors(byte[] codewordBytes, int numDataCodewords) throws ChecksumException {
    int numCodewords = codewordBytes.length;
    // First read into an array of ints
    int[] codewordsInts = new int[numCodewords];
    for (int i = 0; i < numCodewords; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    try {
      rsDecoder.decode(codewordsInts, codewordBytes.length - numDataCodewords);
    } catch (ReedSolomonException ignored) {
      throw ChecksumException.getChecksumInstance();
    }
    // Copy back into array of bytes -- only need to worry about the bytes that were data
    // We don't care about errors in the error-correction codewords
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
  }

  //分割手法の関数 符号長，情報コード数を引数に与えて，同じパラメータを持つRSブロックの個数，n，kの二次元配列を返す
  public static ArrayList<ArrayList<Integer>> nkDivision(int n,int k) {
    ArrayList<ArrayList<Integer>> list = new ArrayList<ArrayList <Integer>>();

    ArrayList<Integer> nArray = new ArrayList<Integer>();
    ArrayList<Integer> kArray = new ArrayList<Integer>();

    if (k == 1 && n > 255) {
      nArray.add(255);
      kArray.add(k);
    } else {
      nArray.add(n);
      kArray.add(k);
    }

    int cnt = 0;
    int x = n;
    int y = 0;

    // x > 255 のとき分割したRSブロックの符号長が255以下となるように
    while (x > 155 && k != 1) {
      y = nArray.size();
      boolean flag = false;
      for (int i = (int) Math.pow(2,cnt) - 1; i < y; i++) {

        if (nArray.get(i) % 2 == 0) {
          nArray.add(nArray.get(i) / 2);
          nArray.add(nArray.get(i) / 2);
          x = nArray.get(i) / 2;
        } else {
          nArray.add(nArray.get(i) / 2);
          nArray.add(nArray.get(i) / 2 + 1);
          x = nArray.get(i) / 2 + 1;
        }

        int knum = kArray.get(i) / 2;
        if (kArray.get(i) % 2 == 0) {
          kArray.add(kArray.get(i) / 2);
          kArray.add(kArray.get(i) / 2);
        } else {
          kArray.add(kArray.get(i) / 2);
          kArray.add(kArray.get(i) / 2 + 1);
        }
        // 分割した値が1だったら分割を中止する
        if (knum == 1) {
            flag = true;
        }
      }
      cnt++;
      if (flag) {
        break;
      }
    }

    // 得られた値を偶数ー奇数の順に並べる
    ArrayList<Integer> onArray = new ArrayList<Integer>();
    ArrayList<Integer> okArray = new ArrayList<Integer>();

    for (int i = (int) Math.pow(2,cnt) - 1; i < nArray.size(); i++) {
      if (nArray.get(i) % 2 == 0) {
        if (nArray.get(i) > 255) {
          onArray.add(255);
        } else {
          onArray.add(nArray.get(i));
        }
      }
      if (kArray.get(i) % 2 == 0) {
        okArray.add(kArray.get(i));
      }
    }

    for (int i = (int) Math.pow(2,cnt) - 1; i < nArray.size(); i++) {
      if (nArray.get(i) % 2 == 1) {
        if (nArray.get(i) > 255) {
          onArray.add(255);
        } else {
          onArray.add(nArray.get(i));
        }
      }
      if (kArray.get(i) % 2 == 1) {
        okArray.add(kArray.get(i));
      }
    }

    // debug
    // for (int i = 0; i < onArray.size(); i++) {
    //     System.out.println("onArray[" + i + "] = " + onArray.get(i));
    //     System.out.println("okArray[" + i + "] = " + okArray.get(i));
    // }


    // count×(n,k)の形式にまとめる
    ArrayList<Integer> cnArray = new ArrayList<Integer>();
    ArrayList<Integer> ckArray = new ArrayList<Integer>();
    ArrayList<Integer> cntNum = new ArrayList<Integer>();

    for (int i = 0; i < onArray.size(); i++) {
      boolean flag = true;
      for (int j = 0; j < cnArray.size(); j++) {
        if (cnArray.get(j).compareTo(onArray.get(i)) == 0 && ckArray.get(j).compareTo(okArray.get(i)) == 0) {
          cntNum.set(j, cntNum.get(j) + 1);
          flag = false;
          break;
        }
      }
      if (flag) {
        cnArray.add(onArray.get(i));
        ckArray.add(okArray.get(i));
        cntNum.add(1);
      }
    }

    list.add(cntNum);
    list.add(cnArray);
    list.add(ckArray);

    return list;
  }

}
