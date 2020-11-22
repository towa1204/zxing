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

/**
 * <p>Encapsulates a block of data within a QR Code. QR Codes may split their data into
 * multiple blocks, each of which is a unit of data and error-correction codewords. Each
 * is represented by an instance of this class.</p>
 *
 * @author Sean Owen
 */
final class DataBlock {

  private final int numDataCodewords;
  private final byte[] codewords;

  private DataBlock(int numDataCodewords, byte[] codewords) {
    this.numDataCodewords = numDataCodewords;
    this.codewords = codewords;
  }

  /**
   * <p>When QR Codes use multiple data blocks, they are actually interleaved.
   * That is, the first byte of data block 1 to n is written, then the second bytes, and so on. This
   * method will separate the data into original blocks.</p>
   *
   * @param rawCodewords bytes as read directly from the QR Code
   * @param version version of the QR Code
   * @param ecLevel error-correction level of the QR Code
   * @return DataBlocks containing original bytes, "de-interleaved" from representation in the
   *         QR Code
   */
  static DataBlock[] getDataBlocks(byte[] rawCodewords,
                                   NewVersion newVersion,
                                   int[] commonRSBlockIndex) {

    if (rawCodewords.length != newVersion.getTotalCodewords()) {
      throw new IllegalArgumentException();
    }

    int numDataBytes = newVersion.getTotalDataCodewords();
    int numRSBlock = newVersion.getECBlocks().getNumBlocks();
    NewVersion.NewECB[] newEcb = newVersion.getECBlocks().getECBlocks();

    int maxNumDataBytes = 0;
    int maxNumEcBytes = 0;

    DataBlock[] result = new DataBlock[numRSBlock];

    // DataBlock[]のそれぞれの大きさを決める処理
    int resultOffset = 0;
    for (int i = 0; i < newEcb.length; i++) {
      for (int j = 0; j < newEcb[i].getCount(); j++) {
        int numDataBytesInBlock = newEcb[i].getDataCodewords();
        int numEcBytesInBlock = newEcb[i].getCodewords() - numDataBytesInBlock;
        result[resultOffset++] = new DataBlock(numDataBytesInBlock, new byte[newEcb[i].getCodewords()]);

        maxNumDataBytes = Math.max(maxNumDataBytes, numDataBytesInBlock);
        maxNumEcBytes = Math.max(maxNumEcBytes, numEcBytesInBlock);
      }
    }

    int position = 0;
    int commonRSBlockOffset = 0;
    // 情報コード部を取り出す処理 共通RSブロックは取得しない
    for (int i = 0; i < maxNumDataBytes; i++) {
      for (int j = 1; j < result.length; j++) {
        if (commonRSBlockIndex[commonRSBlockOffset]  == position) {
          commonRSBlockOffset++;
          position++;
          // jをやり直す その他のRSブロックのj番目を飛ばさないように
          j--;
        } else {
          if (i < result[j].getNumDataCodewords()) {
            result[j].codewords[i] = rawCodewords[position];
            position++;
          }
        }
      }
    }

    // 誤り訂正コード部を取り出す処理 共通RSブロックは取得しない
    for (int i = 0; i < maxNumEcBytes; i++) {
      for (int j = 1; j < result.length; j++) {
        if (commonRSBlockOffset < commonRSBlockIndex.length &&
            commonRSBlockIndex[commonRSBlockOffset]  == position) {
          commonRSBlockOffset++;
          position++;
          // jをやり直す その他のRSブロックのj番目を飛ばさないように
          j--;
        } else {
          if (i < result[j].codewords.length - result[j].getNumDataCodewords()) {
            result[j].codewords[i + result[j].getNumDataCodewords()] = rawCodewords[position];
            position++;
          }
        }
      }
    }

    return result;
  }

  int getNumDataCodewords() {
    return numDataCodewords;
  }

  byte[] getCodewords() {
    return codewords;
  }

}
