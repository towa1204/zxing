package com.google.zxing.qrcode.decoder;

import java.util.ArrayList;

public final class NewVersion {

  private static final int[][][] rsParamArray = {
      {{6, 4, 20}, {8, 4, 18}, {6, 4, 20}, {13, 4, 13}},
      {{8, 4, 36}, {8, 4, 36}, {8, 4, 36}, {14, 4, 30}},
      {{8, 4, 62}, {12, 4, 58}, {10, 4, 60}, {14, 4, 56}},
      {{10, 4, 90}, {14, 4, 86}, {12, 4, 88}, {20, 4, 80}},
      {{12, 4, 122}, {16, 4, 118}, {18, 4, 116}, {28, 4, 106}},
      {{18, 4, 154}, {22, 4, 150}, {28, 4, 144}, {32, 4, 140}},
      {{18, 4, 178}, {24, 4, 172}, {28, 4, 168}, {40, 4, 156}},
      {{20, 4, 222}, {26, 4, 216}, {30, 4, 212}, {36, 4, 206}},
      {{26, 4, 266}, {36, 4, 256}, {36, 4, 256}, {52, 4, 240}},
      {{33, 5, 313}, {43, 5, 303}, {47, 5, 299}, {53, 5, 293}},
      {{33, 5, 371}, {47, 5, 357}, {53, 5, 351}, {65, 5, 339}},
      {{41, 5, 425}, {57, 5, 409}, {63, 5, 403}, {83, 5, 383}},
      {{39, 5, 493}, {61, 5, 471}, {53, 5, 479}, {93, 5, 439}},
      {{49, 5, 532}, {65, 5, 516}, {69, 5, 512}, {101, 5, 480}},
      {{51, 5, 604}, {69, 5, 586}, {75, 5, 580}, {109, 5, 546}},
      {{53, 5, 680}, {93, 5, 640}, {93, 5, 640}, {113, 5, 620}},
      {{67, 5, 748}, {97, 5, 718}, {91, 5, 724}, {119, 5, 696}},
      {{67, 5, 834}, {103, 5, 798}, {117, 5, 784}, {131, 5, 770}},
      {{71, 5, 920}, {101, 5, 890}, {111, 5, 880}, {151, 5, 840}},
      {{89, 5, 996}, {135, 5, 950}, {125, 5, 960}, {135, 5, 950}},
      {{77, 5, 1079}, {143, 5, 1013}, {141, 5, 1015}, {153, 5, 1003}},
      {{93, 5, 1165}, {147, 5, 1111}, {131, 5, 1127}, {165, 5, 1093}},
      {{97, 5, 1267}, {141, 5, 1223}, {145, 5, 1219}, {215, 5, 1149}},
      {{113, 5, 1361}, {175, 5, 1299}, {155, 5, 1319}, {201, 5, 1273}},
      {{109, 5, 1479}, {165, 5, 1423}, {161, 5, 1427}, {255, 5, 1333}},
      {{117, 5, 1589}, {195, 5, 1511}, {207, 5, 1499}, {227, 5, 1479}},
      {{125, 5, 1703}, {223, 5, 1605}, {221, 5, 1607}, {269, 5, 1559}},
      {{145, 5, 1776}, {223, 5, 1698}, {189, 5, 1732}, {281, 5, 1640}},
      {{159, 5, 1892}, {247, 5, 1804}, {239, 5, 1812}, {311, 5, 1740}},
      {{173, 5, 2012}, {229, 5, 1956}, {225, 5, 1960}, {335, 5, 1850}},
      {{185, 5, 2138}, {251, 5, 2072}, {267, 5, 2056}, {353, 5, 1970}},
      {{197, 5, 2268}, {269, 5, 2196}, {245, 5, 2220}, {365, 5, 2100}},
      {{207, 5, 2404}, {287, 5, 2324}, {279, 5, 2332}, {371, 5, 2240}},
      {{219, 5, 2542}, {303, 5, 2458}, {309, 5, 2452}, {371, 5, 2390}},
      {{199, 5, 2677}, {293, 5, 2583}, {313, 5, 2563}, {423, 5, 2453}},
      {{209, 5, 2825}, {305, 5, 2729}, {335, 5, 2699}, {411, 5, 2623}},
      {{217, 5, 2979}, {357, 5, 2839}, {353, 5, 2843}, {467, 5, 2729}},
      {{225, 5, 3137}, {365, 5, 2997}, {367, 5, 2995}, {519, 5, 2843}},
      {{267, 5, 3265}, {373, 5, 3159}, {489, 5, 3043}, {489, 5, 3043}},
      {{273, 5, 3433}, {377, 5, 3329}, {383, 5, 3323}, {527, 5, 3179}},
  };

  private final NewECBlocks ecBlocks;
  private final int totalDataCodewords;
  private final int totalCodewords;

  public NewVersion(int versionNumber,
                     ErrorCorrectionLevel ecLevel,
                     int contentSize) {

    int[] rsParam = getRSParam(versionNumber, ecLevel);

    this.totalDataCodewords = contentSize;
    this.totalCodewords = rsParam[0] + rsParam[2];

    if (rsParam[2] > 255) {
      ArrayList<ArrayList<Integer>> rsBlocksParam;
      // 分割手法を適用
      rsBlocksParam = Decoder.nkDivision(rsParam[2], contentSize - rsParam[1]);

      // 共通RSブロックと他のRSブロックのパラメータが一致したときの処理
      boolean flag = true; // 一致しなかったらtrue, 一致したらfalse
      for (int i = 0; i < rsBlocksParam.get(0).size(); i++) {
        if ((rsParam[0] == rsBlocksParam.get(1).get(i)) &&
            (rsParam[1] == rsBlocksParam.get(2).get(i))) {
          flag = false;
          // RSブロックの数を1つ加算
          rsBlocksParam.get(0).set(i, rsBlocksParam.get(0).get(i) + 1);
          break;
        }
      }

      // 一致しなかった場合，一致した場合で配列の要素が1つ変わる
      NewECB[] ecbArray = flag ? new NewECB[rsBlocksParam.get(0).size() + 1] :
                                 new NewECB[rsBlocksParam.get(0).size()];
      int rsBlocksOffset = 0;
      // 一致しなかった場合，共通RSブロックを予め追加しておく
      if (flag) {
        ecbArray[0] = new NewECB(1, rsParam[0], rsParam[1]);
        rsBlocksOffset++;
      }

      //nkParamをそれぞれECBでインスタンス化
      for (int i = 0; i < rsBlocksParam.get(0).size(); i++) {
        ecbArray[i + rsBlocksOffset] = new NewECB(rsBlocksParam.get(0).get(i),
                                 rsBlocksParam.get(1).get(i),
                                 rsBlocksParam.get(2).get(i));
      }

      this.ecBlocks = new NewECBlocks(ecbArray);
    } else {
      // 共通RSブロックと他のRSブロックのパラメータが一致したとき
      if ((rsParam[0] == rsParam[2]) && (rsParam[1] == contentSize - rsParam[1])) {
        this.ecBlocks = new NewECBlocks(new NewECB(2, rsParam[0], rsParam[1]));
      } else {
        this.ecBlocks = new NewECBlocks(new NewECB(1,rsParam[0],rsParam[1]),
            new NewECB(1,rsParam[2],contentSize - rsParam[1]));
      }
    }
  }

  // インスタンス化時に指定したk'のサイズを取得
  public int getTotalDataCodewords() {
    return totalDataCodewords;
  }

  public NewECBlocks getECBlocks() {
    return ecBlocks;
  }

  public int getTotalCodewords() {
    return totalCodewords;
  }

  // クラスメソッド：指定した型番の総コード語数を取得
  public static int getTotalCodewords(int versionNumber) {
    return rsParamArray[versionNumber - 1][0][0] + rsParamArray[versionNumber - 1][0][2];
  }

  // クラスメソッド：型番・誤り訂正レベルを指定して共通RSブロックの(n,k)パラメータを取得
  public static int[] getCommonRSParam(int versionNumber, ErrorCorrectionLevel ecLevel) {
    int ecLevelNum = getECLevelNum(ecLevel);
    return rsParamArray[versionNumber - 1][ecLevelNum];
  }

  private static int getECLevelNum(ErrorCorrectionLevel ecLevel) {
    int ecLevelNum = 0;
    switch (ecLevel) {
    case L:
      ecLevelNum = 0;
      break;
    case M:
      ecLevelNum = 1;
      break;
    case Q:
      ecLevelNum = 2;
      break;
    case H:
      ecLevelNum = 3;
      break;
    }
    return ecLevelNum;
  }

  public static final class NewECBlocks {
    private final NewECB[] ecBlocks;

    NewECBlocks(NewECB... ecBlocks) {
      this.ecBlocks = ecBlocks;
    }

    public int getNumBlocks() {
      int total = 0;
      for (NewECB ecBlock : ecBlocks) {
        total += ecBlock.getCount();
      }
      return total;
    }

    public NewECB[] getECBlocks() {
      return ecBlocks;
    }

  }

  public static final class NewECB {
    private final int count;
    private final int codewords;
    private final int dataCodewords;

    NewECB(int count, int codewords, int dataCodewords) {
      this.count = count;
      this.codewords = codewords;
      this.dataCodewords = dataCodewords;
    }

    public int getCount() {
      return count;
    }

    public int getCodewords() {
      return codewords;
    }

    public int getDataCodewords() {
      return dataCodewords;
    }

  }

  private static int[] getRSParam(int versionNumber,
                                    ErrorCorrectionLevel ecLevel) {
    return rsParamArray[versionNumber - 1][getECLevelNum(ecLevel)];
  }

}
