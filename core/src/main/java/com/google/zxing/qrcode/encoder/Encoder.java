/*
 * Copyright 2008 ZXing authors
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

package com.google.zxing.qrcode.encoder;

import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.CharacterSetECI;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.NewVersion;
import com.google.zxing.qrcode.decoder.Version;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

/**
 * @author satorux@google.com (Satoru Takabayashi) - creator
 * @author dswitkin@google.com (Daniel Switkin) - ported from C++
 */
public final class Encoder {

  // The original table is defined in the table 5 of JISX0510:2004 (p.19).
  private static final int[] ALPHANUMERIC_TABLE = {
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x00-0x0f
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x10-0x1f
      36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  // 0x20-0x2f
      0,   1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1,  // 0x30-0x3f
      -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 0x40-0x4f
      25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1,  // 0x50-0x5f
  };

  static final String DEFAULT_BYTE_MODE_ENCODING = "ISO-8859-1";

  private Encoder() {
  }

  // The mask penalty calculation is complicated.  See Table 21 of JISX0510:2004 (p.45) for details.
  // Basically it applies four rules and summate all penalties.
  private static int calculateMaskPenalty(ByteMatrix matrix) {
    return MaskUtil.applyMaskPenaltyRule1(matrix)
        + MaskUtil.applyMaskPenaltyRule2(matrix)
        + MaskUtil.applyMaskPenaltyRule3(matrix)
        + MaskUtil.applyMaskPenaltyRule4(matrix);
  }

  /**
   * @param content text to encode
   * @param ecLevel error correction level to use
   * @return {@link QRCode} representing the encoded QR code
   * @throws WriterException if encoding can't succeed, because of for example invalid content
   *   or configuration
   * @throws FormatException format error
   */
  public static QRCode encode(String content, ErrorCorrectionLevel ecLevel) throws WriterException, FormatException {
    return encode(content, ecLevel, null);
  }

  public static QRCode encode(String content,
                              ErrorCorrectionLevel ecLevel,
                              Map<EncodeHintType,?> hints) throws WriterException {

    // Determine what character encoding has been specified by the caller, if any
    String encoding = DEFAULT_BYTE_MODE_ENCODING;
    boolean hasEncodingHint = hints != null && hints.containsKey(EncodeHintType.CHARACTER_SET);
    if (hasEncodingHint) {
      encoding = hints.get(EncodeHintType.CHARACTER_SET).toString();
    }

    // Pick an encoding mode appropriate for the content. Note that this will not attempt to use
    // multiple modes / segments even if that were more efficient. Twould be nice.
    Mode mode = chooseMode(content, encoding);

    // This will store the header information, like mode and
    // length, as well as "header" segments like an ECI segment.
    BitArray headerBits = new BitArray();

    // Append ECI segment if applicable
    if (mode == Mode.BYTE && hasEncodingHint) {
      CharacterSetECI eci = CharacterSetECI.getCharacterSetECIByName(encoding);
      if (eci != null) {
        appendECI(eci, headerBits);
      }
    }

    // Append the FNC1 mode header for GS1 formatted data if applicable
    boolean hasGS1FormatHint = hints != null && hints.containsKey(EncodeHintType.GS1_FORMAT);
    if (hasGS1FormatHint && Boolean.parseBoolean(hints.get(EncodeHintType.GS1_FORMAT).toString())) {
      // GS1 formatted codes are prefixed with a FNC1 in first position mode header
      appendModeInfo(Mode.FNC1_FIRST_POSITION, headerBits);
    }

    // (With ECI in place,) Write the mode marker
    appendModeInfo(mode, headerBits);

    // Collect data within the main segment, separately, to count its size if needed. Don't add it to
    // main payload yet.
    BitArray dataBits = new BitArray();
    appendBytes(content, mode, dataBits, encoding);

    Version version;
    int versionNumber = 0; // あまりよくない
    // 型番を予め設定していた場合，if文の中へ
    // 型番は予め設定されているものとして設計する
    if (hints != null && hints.containsKey(EncodeHintType.QR_VERSION)) {
      // 型番を取得
      versionNumber = Integer.parseInt(hints.get(EncodeHintType.QR_VERSION).toString());
      version = Version.getVersionForNumber(versionNumber);
      // 入力文字数の制限は同じなので変えなくて良さそう
      int bitsNeeded = calculateBitsNeeded(mode, headerBits, dataBits, version);
      if (!willFit(bitsNeeded, version, ecLevel)) {
        throw new WriterException("Data too big for requested version");
      }
    } else {
      // ここを通ることは想定していない
      version = recommendVersion(ecLevel, mode, headerBits, dataBits);
    }

    BitArray headerAndDataBits = new BitArray();
    headerAndDataBits.appendBitArray(headerBits);
    // Find "length" of main segment and write it
    int numLetters = mode == Mode.BYTE ? dataBits.getSizeInBytes() : content.length();
    // versionから文字数指示子のビット数をとっているだけなので変えなくてよさそう
    appendLengthInfo(numLetters, version, mode, headerAndDataBits);
    // Put data together into the overall payload
    headerAndDataBits.appendBitArray(dataBits);

    Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
    // numDataBytesは提案手法ではk'の上限の役割
    int numDataBytes = version.getTotalCodewords() - ecBlocks.getTotalECCodewords();

    // Terminate the bits properly.
    terminateBits(numDataBytes, headerAndDataBits);
    // headerAndDataBits.getSizeInBytes() = k'
    NewVersion newVersion = new NewVersion(versionNumber, ecLevel,
                                           headerAndDataBits.getSizeInBytes());

    // Interleave data bits with error correction code.
    // 書き換える必要あり
    BitArray finalBits = interleaveWithECBytes(headerAndDataBits,
                                               newVersion);

    QRCode qrCode = new QRCode();

    qrCode.setECLevel(ecLevel);
    qrCode.setMode(mode);
    qrCode.setVersion(version);

    //  Choose the mask pattern and set to "qrCode".
    int dimension = version.getDimensionForVersion();
    ByteMatrix matrix = new ByteMatrix(dimension, dimension);

    // Enable manual selection of the pattern to be used via hint
    int maskPattern = -1;
    if (hints != null && hints.containsKey(EncodeHintType.QR_MASK_PATTERN)) {
      int hintMaskPattern = Integer.parseInt(hints.get(EncodeHintType.QR_MASK_PATTERN).toString());
      maskPattern = QRCode.isValidMaskPattern(hintMaskPattern) ? hintMaskPattern : -1;
    }

    if (maskPattern == -1) {
      // 変えなくてよさそう
      maskPattern = chooseMaskPattern(finalBits, ecLevel, version, matrix);
    }
    qrCode.setMaskPattern(maskPattern);

    // Build the matrix and set it to "qrCode".
    // 変えなくてよさそう?
    MatrixUtil.buildMatrix(finalBits, ecLevel, version, maskPattern, matrix);
    qrCode.setMatrix(matrix);

    // 誤りを付加するメソッドを置く

    return qrCode;
  }

  // 評価実験1 ランダム誤りを発生させるメソッド
  public static void appendBitsError(ByteMatrix matrix) {
    Random prob = new Random();
    // 10％の確率
    final int threshold = 10;
    for (int i = 0; i < matrix.getHeight(); i++) {
      for (int j = 0; j < matrix.getWidth(); j++) {
        // 10％の確率で誤りを発生させる
        if (prob.nextInt(100) < threshold) {
          // i,jの位置にあるビットが0だったら1，1だったら0を代入
          if (matrix.get(i, j) == 0) {
            matrix.set(i, j, 1);
          } else {
            matrix.set(i, j, 0);
          }
        }
      }
    }
  }

  /**
   * Decides the smallest version of QR code that will contain all of the provided data.
   *
   * @throws WriterException if the data cannot fit in any version
   */
  private static Version recommendVersion(ErrorCorrectionLevel ecLevel,
                                          Mode mode,
                                          BitArray headerBits,
                                          BitArray dataBits) throws WriterException {
    // Hard part: need to know version to know how many bits length takes. But need to know how many
    // bits it takes to know version. First we take a guess at version by assuming version will be
    // the minimum, 1:
    int provisionalBitsNeeded = calculateBitsNeeded(mode, headerBits, dataBits, Version.getVersionForNumber(1));
    Version provisionalVersion = chooseVersion(provisionalBitsNeeded, ecLevel);

    // Use that guess to calculate the right version. I am still not sure this works in 100% of cases.
    int bitsNeeded = calculateBitsNeeded(mode, headerBits, dataBits, provisionalVersion);
    return chooseVersion(bitsNeeded, ecLevel);
  }

  private static int calculateBitsNeeded(Mode mode,
                                         BitArray headerBits,
                                         BitArray dataBits,
                                         Version version) {
    return headerBits.getSize() + mode.getCharacterCountBits(version) + dataBits.getSize();
  }

  /**
   * @return the code point of the table used in alphanumeric mode or
   *  -1 if there is no corresponding code in the table.
   */
  static int getAlphanumericCode(int code) {
    if (code < ALPHANUMERIC_TABLE.length) {
      return ALPHANUMERIC_TABLE[code];
    }
    return -1;
  }

  public static Mode chooseMode(String content) {
    return chooseMode(content, null);
  }

  /**
   * Choose the best mode by examining the content. Note that 'encoding' is used as a hint;
   * if it is Shift_JIS, and the input is only double-byte Kanji, then we return {@link Mode#KANJI}.
   */
  private static Mode chooseMode(String content, String encoding) {
    if ("Shift_JIS".equals(encoding) && isOnlyDoubleByteKanji(content)) {
      // Choose Kanji mode if all input are double-byte characters
      return Mode.KANJI;
    }
    boolean hasNumeric = false;
    boolean hasAlphanumeric = false;
    for (int i = 0; i < content.length(); ++i) {
      char c = content.charAt(i);
      if (c >= '0' && c <= '9') {
        hasNumeric = true;
      } else if (getAlphanumericCode(c) != -1) {
        hasAlphanumeric = true;
      } else {
        return Mode.BYTE;
      }
    }
    if (hasAlphanumeric) {
      return Mode.ALPHANUMERIC;
    }
    if (hasNumeric) {
      return Mode.NUMERIC;
    }
    return Mode.BYTE;
  }

  private static boolean isOnlyDoubleByteKanji(String content) {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException ignored) {
      return false;
    }
    int length = bytes.length;
    if (length % 2 != 0) {
      return false;
    }
    for (int i = 0; i < length; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      if ((byte1 < 0x81 || byte1 > 0x9F) && (byte1 < 0xE0 || byte1 > 0xEB)) {
        return false;
      }
    }
    return true;
  }

  private static int chooseMaskPattern(BitArray bits,
                                       ErrorCorrectionLevel ecLevel,
                                       Version version,
                                       ByteMatrix matrix) throws WriterException {

    int minPenalty = Integer.MAX_VALUE;  // Lower penalty is better.
    int bestMaskPattern = -1;
    // We try all mask patterns to choose the best one.
    for (int maskPattern = 0; maskPattern < QRCode.NUM_MASK_PATTERNS; maskPattern++) {
      MatrixUtil.buildMatrix(bits, ecLevel, version, maskPattern, matrix);
      int penalty = calculateMaskPenalty(matrix);
      if (penalty < minPenalty) {
        minPenalty = penalty;
        bestMaskPattern = maskPattern;
      }
    }
    return bestMaskPattern;
  }

  private static Version chooseVersion(int numInputBits, ErrorCorrectionLevel ecLevel) throws WriterException {
    for (int versionNum = 1; versionNum <= 40; versionNum++) {
      Version version = Version.getVersionForNumber(versionNum);
      if (willFit(numInputBits, version, ecLevel)) {
        return version;
      }
    }
    throw new WriterException("Data too big");
  }

  /**
   * @return true if the number of input bits will fit in a code with the specified version and
   * error correction level.
   */
  private static boolean willFit(int numInputBits, Version version, ErrorCorrectionLevel ecLevel) {
      // In the following comments, we use numbers of Version 7-H.
      // numBytes = 196
      int numBytes = version.getTotalCodewords();
      // getNumECBytes = 130
      Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
      int numEcBytes = ecBlocks.getTotalECCodewords();
      // getNumDataBytes = 196 - 130 = 66
      int numDataBytes = numBytes - numEcBytes;
      int totalInputBytes = (numInputBits + 7) / 8;
      return numDataBytes >= totalInputBytes;
  }

  /**
   * Terminate bits as described in 8.4.8 and 8.4.9 of JISX0510:2004 (p.24).
   */
  static void terminateBits(int numDataBytes, BitArray bits) throws WriterException {
    // numDataBytesが型番・誤り訂正レベルにおける情報コードの上限の役割
    int capacity = numDataBytes * 8;
    if (bits.getSize() > capacity) {
      throw new WriterException("data bits cannot fit in the QR Code" + bits.getSize() + " > " +
          capacity);
    }
    // 終端パターン
    for (int i = 0; i < 4 && bits.getSize() < capacity; ++i) {
      bits.appendBit(false);
    }
    // Append termination bits. See 8.4.8 of JISX0510:2004 (p.24) for details.
    // If the last byte isn't 8-bit aligned, we'll add padding bits.
    // 埋め草ビット
    int numBitsInLastByte = bits.getSize() & 0x07;
    if (numBitsInLastByte > 0) {
      for (int i = numBitsInLastByte; i < 8; i++) {
        bits.appendBit(false);
      }
    }
  }

  /**
   * Get number of data bytes and number of error correction bytes for block id "blockID". Store
   * the result in "numDataBytesInBlock", and "numECBytesInBlock". See table 12 in 8.5.1 of
   * JISX0510:2004 (p.30)
   */
  static void getNumDataBytesAndNumECBytesForBlockID(int numTotalBytes,
                                                     int numDataBytes,
                                                     int numRSBlocks,
                                                     int blockID,
                                                     int[] numDataBytesInBlock,
                                                     int[] numECBytesInBlock) throws WriterException {
    if (blockID >= numRSBlocks) {
      throw new WriterException("Block ID too large");
    }
    // numRsBlocksInGroup2 = 196 % 5 = 1
    int numRsBlocksInGroup2 = numTotalBytes % numRSBlocks;
    // numRsBlocksInGroup1 = 5 - 1 = 4
    int numRsBlocksInGroup1 = numRSBlocks - numRsBlocksInGroup2;
    // numTotalBytesInGroup1 = 196 / 5 = 39
    int numTotalBytesInGroup1 = numTotalBytes / numRSBlocks;
    // numTotalBytesInGroup2 = 39 + 1 = 40
    int numTotalBytesInGroup2 = numTotalBytesInGroup1 + 1;
    // numDataBytesInGroup1 = 66 / 5 = 13
    int numDataBytesInGroup1 = numDataBytes / numRSBlocks;
    // numDataBytesInGroup2 = 13 + 1 = 14
    int numDataBytesInGroup2 = numDataBytesInGroup1 + 1;
    // numEcBytesInGroup1 = 39 - 13 = 26
    int numEcBytesInGroup1 = numTotalBytesInGroup1 - numDataBytesInGroup1;
    // numEcBytesInGroup2 = 40 - 14 = 26
    int numEcBytesInGroup2 = numTotalBytesInGroup2 - numDataBytesInGroup2;
    // Sanity checks.
    // 26 = 26
    if (numEcBytesInGroup1 != numEcBytesInGroup2) {
      throw new WriterException("EC bytes mismatch");
    }
    // 5 = 4 + 1.
    if (numRSBlocks != numRsBlocksInGroup1 + numRsBlocksInGroup2) {
      throw new WriterException("RS blocks mismatch");
    }
    // 196 = (13 + 26) * 4 + (14 + 26) * 1
    if (numTotalBytes !=
        ((numDataBytesInGroup1 + numEcBytesInGroup1) *
            numRsBlocksInGroup1) +
            ((numDataBytesInGroup2 + numEcBytesInGroup2) *
                numRsBlocksInGroup2)) {
      throw new WriterException("Total bytes mismatch");
    }

    if (blockID < numRsBlocksInGroup1) {
      numDataBytesInBlock[0] = numDataBytesInGroup1;
      numECBytesInBlock[0] = numEcBytesInGroup1;
    } else {
      numDataBytesInBlock[0] = numDataBytesInGroup2;
      numECBytesInBlock[0] = numEcBytesInGroup2;
    }
  }

  /**
   * Interleave "bits" with corresponding error correction bytes. On success, store the result in
   * "result". The interleave rule is complicated. See 8.6 of JISX0510:2004 (p.37) for details.
   */
  static BitArray interleaveWithECBytes(BitArray bits,
                                        NewVersion newVersion) throws WriterException {

    // numDataBytes = k' に注意
    int numDataBytes = newVersion.getTotalDataCodewords();
    int numRSBlocks = newVersion.getECBlocks().getNumBlocks();
    NewVersion.NewECB[] newEcb = newVersion.getECBlocks().getECBlocks();

    // debug用
    System.out.println("k' = " + numDataBytes);
    for (NewVersion.NewECB ecb : newEcb) {
      System.out.println(ecb.getCount() + "×" +
    "(" + ecb.getCodewords() + "," + ecb.getDataCodewords() + ")");
    }

    // "bits" must have "getNumDataBytes" bytes of data.
    if (bits.getSizeInBytes() != numDataBytes) {
      throw new WriterException("Number of bits and data bytes does not match");
    }

    // Step 1.  Divide data bytes into blocks and generate error correction bytes for them. We'll
    // store the divided data bytes blocks and error correction bytes blocks into "blocks".
    int dataBytesOffset = 0;
    int maxNumDataBytes = 0;
    int maxNumEcBytes = 0;

    // Since, we know the number of reedsolmon blocks, we can initialize the vector with the number.
    // collectionだと用途が限られるのでArrayListへ変更
    ArrayList<BlockPair> blocks = new ArrayList<>(numRSBlocks);

    // 情報コードを取り出しRS符号化，情報コードと得た誤り訂正コードをBlockPair型として格納
    for (int i = 0; i < newEcb.length; i++) {
      for (int j = 0; j < newEcb[i].getCount(); j++) {
        int numDataBytesInBlock = newEcb[i].getDataCodewords();
        int numEcBytesInBlock = newEcb[i].getCodewords() - numDataBytesInBlock;
        byte[] dataBytes = new byte[numDataBytesInBlock];
        bits.toBytes(8 * dataBytesOffset, dataBytes, 0, numDataBytesInBlock);
        byte[] ecBytes = generateECBytes(dataBytes, numEcBytesInBlock);
        blocks.add(new BlockPair(dataBytes, ecBytes));

        maxNumDataBytes = Math.max(maxNumDataBytes, numDataBytesInBlock);
        maxNumEcBytes = Math.max(maxNumEcBytes, ecBytes.length);
        dataBytesOffset += numDataBytesInBlock;
      }
    }
    // いらなさそうな気もするけど一応
    if (numDataBytes != dataBytesOffset) {
      throw new WriterException("Data bytes does not match offset");
    }

    BitArray result = new BitArray();
    // 共通RSブロックの格納位置を格納した配列
    int commonRSBlockCodewords = newVersion.getECBlocks().getECBlocks()[0].getCodewords();
    int[] commonRSBlockIndex = getCommonRSBlockIndex(
        commonRSBlockCodewords, newVersion.getTotalCodewords() - commonRSBlockCodewords);
    // バイト列から見た挿入箇所 最後は総コード語数になるはず
    int position = 0;
    int commonRSBlockOffset = 0;
    // 共通RSブロックの情報コード部の大きさ
    int commonDataBytesLength = blocks.get(0).getDataBytes().length;

    // 共通RSブロックのバイト列をcommonRSByteにまとめる
    byte[] commonRSByte = new byte[commonRSBlockCodewords];
    for (int i = 0; i < commonRSBlockCodewords; i++) {
      if (i < commonDataBytesLength) {
        commonRSByte[i] = blocks.get(0).getDataBytes()[i];
      } else {
        commonRSByte[i] = blocks.get(0).getErrorCorrectionBytes()[i - commonDataBytesLength];
      }
    }

    // 共通RSブロックと情報コード部を埋め込む処理
    for (int i = 0; i < maxNumDataBytes; i++) {
      for (int j = 1; j < blocks.size(); j++) {
        // 共通RSブロックのバイトを置く位置とループ回数が一致したとき
        if (commonRSBlockIndex[commonRSBlockOffset] == position) {
          // 共通RSBlockを代入
//          // debug
//          System.out.println("commonRSBlockOffset = " + commonRSBlockOffset);
          result.appendBits(commonRSByte[commonRSBlockOffset++], 8);
          position++;
          // jをやり直す その他のRSブロックのj番目を飛ばさないように
          j--;
        } else {
          // その他RSBlockを代入
          byte[] dataBytes = blocks.get(j).getDataBytes();
          if (i < dataBytes.length) {
            result.appendBits(dataBytes[i], 8);
            position++;
          }
        }
      }
    }

    // 共通RSブロックと誤り訂正コード部を埋め込む処理
    for (int i = 0; i < maxNumEcBytes; i++) {
      for (int j = 1; j < blocks.size(); j++) {
        if (commonRSBlockOffset < commonRSBlockIndex.length &&
            commonRSBlockIndex[commonRSBlockOffset] == position) {
//          // 共通RSBlockを代入
//          System.out.println("commonRSBlockOffset = " + commonRSBlockOffset);
          result.appendBits(commonRSByte[commonRSBlockOffset++], 8);
          position++;
          // jをやり直す その他のRSブロックのj番目を飛ばさないように
          j--;
        } else {
          // その他RSBlockを代入
          byte[] ecBytes = blocks.get(j).getErrorCorrectionBytes();
          if (i < ecBytes.length) {
            result.appendBits(ecBytes[i], 8);
            position++;
          }
        }
      }
    }
    System.out.println("position = " + position);
    int numTotalBytes = newVersion.getTotalCodewords();
    if (numTotalBytes != result.getSizeInBytes()) {  // Should be same.
      throw new WriterException("Interleaving error: " + numTotalBytes + " and " +
          result.getSizeInBytes() + " differ.");
    }

    return result;
  }

  // 共通RSブロックの符号長とその他のRSブロックの符号長の総和を与えて
  // 共通RSブロックを埋め込む位置を格納した配列を返す関数
  // かなり効率の悪いアルゴリズムなのでリファクタリング必要
  public static int[] getCommonRSBlockIndex(int num1, int num2) {
    // 小さい方をnum1, 大きい方をnum2とする
    if (num1 > num2) {
      int swap = num1;
      num1 = num2;
      num2 = swap;
    }
    // num1とnum2の最大公約数を求める
    int gcd = gcd(num1,num2);
    // num1, num2を最大公約数で割った数をα, βとする
    int alpha = num1 / gcd;
    int beta = num2 / gcd;

    // β = q * α + r (r < α)
    // 1 : q (余りr)となる
    int quotient = beta / alpha;
    int remainder = beta % alpha;

    // gcd * alpha = num1 回, 1 + q + sw 個出力する
    int codewordsOffset = 0;
    int position = 0;
    int[] commonRSBlockIndex = new int[num1];
    for (int g = 0; g < gcd; g++) {
      int sw = remainder == 0 ? 0 : 1;
      for (int i = 0; i < alpha; i++) {
        for (int j = 0; j < (quotient + 1) + sw; j++) {
          if (j == 0) {
            // 共通RSブロックを出力
            commonRSBlockIndex[codewordsOffset++] = position;
          }
          position++;
        }
        if (i + 1 == remainder) {
          sw = 0;
        }
      }
    }
//    // debug
//    System.out.println("position = " + position);
//    for (int i = 0; i < commonRSBlockIndex.length; i++) {
//      System.out.println("[" + i + "] = " + commonRSBlockIndex[i]);
//    }
    return commonRSBlockIndex;
  }

  public static int gcd(int a, int b) {
    if (b == 0) {
      return a;
    }
    return gcd(b, a % b);
  }

  static byte[] generateECBytes(byte[] dataBytes, int numEcBytesInBlock) {
    int numDataBytes = dataBytes.length;
    int[] toEncode = new int[numDataBytes + numEcBytesInBlock];
    for (int i = 0; i < numDataBytes; i++) {
      toEncode[i] = dataBytes[i] & 0xFF;
    }
    new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256).encode(toEncode, numEcBytesInBlock);

    byte[] ecBytes = new byte[numEcBytesInBlock];
    for (int i = 0; i < numEcBytesInBlock; i++) {
      ecBytes[i] = (byte) toEncode[numDataBytes + i];
    }
    return ecBytes;
  }

  /**
   * Append mode info. On success, store the result in "bits".
   */
  static void appendModeInfo(Mode mode, BitArray bits) {
    bits.appendBits(mode.getBits(), 4);
  }


  /**
   * Append length info. On success, store the result in "bits".
   */
  static void appendLengthInfo(int numLetters, Version version, Mode mode, BitArray bits) throws WriterException {
    int numBits = mode.getCharacterCountBits(version);
    if (numLetters >= (1 << numBits)) {
      throw new WriterException(numLetters + " is bigger than " + ((1 << numBits) - 1));
    }
    bits.appendBits(numLetters, numBits);
  }

  /**
   * Append "bytes" in "mode" mode (encoding) into "bits". On success, store the result in "bits".
   */
  static void appendBytes(String content,
                          Mode mode,
                          BitArray bits,
                          String encoding) throws WriterException {
    switch (mode) {
      case NUMERIC:
        appendNumericBytes(content, bits);
        break;
      case ALPHANUMERIC:
        appendAlphanumericBytes(content, bits);
        break;
      case BYTE:
        append8BitBytes(content, bits, encoding);
        break;
      case KANJI:
        appendKanjiBytes(content, bits);
        break;
      default:
        throw new WriterException("Invalid mode: " + mode);
    }
  }

  static void appendNumericBytes(CharSequence content, BitArray bits) {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int num1 = content.charAt(i) - '0';
      if (i + 2 < length) {
        // Encode three numeric letters in ten bits.
        int num2 = content.charAt(i + 1) - '0';
        int num3 = content.charAt(i + 2) - '0';
        bits.appendBits(num1 * 100 + num2 * 10 + num3, 10);
        i += 3;
      } else if (i + 1 < length) {
        // Encode two numeric letters in seven bits.
        int num2 = content.charAt(i + 1) - '0';
        bits.appendBits(num1 * 10 + num2, 7);
        i += 2;
      } else {
        // Encode one numeric letter in four bits.
        bits.appendBits(num1, 4);
        i++;
      }
    }
  }

  static void appendAlphanumericBytes(CharSequence content, BitArray bits) throws WriterException {
    int length = content.length();
    int i = 0;
    while (i < length) {
      int code1 = getAlphanumericCode(content.charAt(i));
      if (code1 == -1) {
        throw new WriterException();
      }
      if (i + 1 < length) {
        int code2 = getAlphanumericCode(content.charAt(i + 1));
        if (code2 == -1) {
          throw new WriterException();
        }
        // Encode two alphanumeric letters in 11 bits.
        bits.appendBits(code1 * 45 + code2, 11);
        i += 2;
      } else {
        // Encode one alphanumeric letter in six bits.
        bits.appendBits(code1, 6);
        i++;
      }
    }
  }

  static void append8BitBytes(String content, BitArray bits, String encoding)
      throws WriterException {
    byte[] bytes;
    try {
      bytes = content.getBytes(encoding);
    } catch (UnsupportedEncodingException uee) {
      throw new WriterException(uee);
    }
    for (byte b : bytes) {
      bits.appendBits(b, 8);
    }
  }

  static void appendKanjiBytes(String content, BitArray bits) throws WriterException {
    byte[] bytes;
    try {
      bytes = content.getBytes("Shift_JIS");
    } catch (UnsupportedEncodingException uee) {
      throw new WriterException(uee);
    }
    if (bytes.length % 2 != 0) {
      throw new WriterException("Kanji byte size not even");
    }
    int maxI = bytes.length - 1; // bytes.length must be even
    for (int i = 0; i < maxI; i += 2) {
      int byte1 = bytes[i] & 0xFF;
      int byte2 = bytes[i + 1] & 0xFF;
      int code = (byte1 << 8) | byte2;
      int subtracted = -1;
      if (code >= 0x8140 && code <= 0x9ffc) {
        subtracted = code - 0x8140;
      } else if (code >= 0xe040 && code <= 0xebbf) {
        subtracted = code - 0xc140;
      }
      if (subtracted == -1) {
        throw new WriterException("Invalid byte sequence");
      }
      int encoded = ((subtracted >> 8) * 0xc0) + (subtracted & 0xff);
      bits.appendBits(encoded, 13);
    }
  }

  private static void appendECI(CharacterSetECI eci, BitArray bits) {
    bits.appendBits(Mode.ECI.getBits(), 4);
    // This is correct for values up to 127, which is all we need now.
    bits.appendBits(eci.getValue(), 8);
  }

}
