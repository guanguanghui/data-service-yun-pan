import java.util.Scanner;
public class Sixteen {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        // 总钱数
        int N = scanner.nextInt();
        // 购买物品个数
        int m = scanner.nextInt();
        int[] f = new int[N + 1];
        // 分组，goods[i][0]为主件，goods[i][1]为附件1，goods[i][2]为附件2
        Good[][] goods1= new Good[60][4];

        for (int i = 1; i <= m; i++) {
            // v 表示该物品的价格
            int v = scanner.nextInt();
            // p 表示该物品的重要度
            int p = scanner.nextInt();
            int q = scanner.nextInt();

            Good t = new Good(v, v * p);
            if (q == 0) {
                goods1[i][0] = t;
            } else {
                if (goods1[q][1] == null) {
                    goods1[q][1] = t;
                } else {
                    goods1[q][2] = t;
                }
            }
        }

        for (int i = 1; i <= m; i++) {
            for (int j = N; j >= 0 && goods1[i][0] != null; j--) {
                //以下代码从分组中选择价值最大的。共五种情况：不选主件，选主件，选附件1和主件，选附件2和主件，选附件1和附件2和主件
                Good master = goods1[i][0];
                int max = f[j];
                if (j >= master.v && max < f[j - master.v] + master.vp) {
                    max = f[j - master.v] + master.vp;
                }
                int vt;
                if (goods1[i][1] != null) {
                    if (j >= (vt = master.v + goods1[i][1].v)
                            && max <  f[j - vt] + master.vp + goods1[i][1].vp) {
                        max = f[j - vt] + master.vp + goods1[i][1].vp;
                    }
                }
                if (goods1[i][2] != null) {
                    if (j >= (vt = master.v + goods1[i][1].v + goods1[i][2].v)
                            && max < f[j - vt] + master.vp + goods1[i][1].vp + goods1[i][2].vp) {
                        max = f[j - vt] + master.vp + goods1[i][1].vp + goods1[i][2].vp;
                    }
                }
                f[j] = max;
            }
        }

        System.out.println(f[N]);
    }
}

class Good {
    int v;
    int vp;
    public Good(int v, int vp) {
        this.v = v;
        this.vp = vp;
    }
}