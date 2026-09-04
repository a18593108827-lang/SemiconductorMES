package com.mes.spc.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** I-MR：用相邻点的移动极差估 σ，再算控制限 / Cpk。 */
public final class SpcImr {

    public static final int CPK_MIN_N = 25;
    public static final int SCALE = 8;
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal SIX = new BigDecimal("6");
    /**
     * 常数 d2。相邻两点差的平均，不能直接当「标准差」，
     * 要除以 1.128 才换算成 σ。这是教科书里的数，不是拍脑袋。
     */
    private static final BigDecimal D2 = new BigDecimal("1.128");

    /** 工具类，不许 new。 */
    private SpcImr() {
    }

    /** 这串点的平均值。空点先扔掉；一个有效数字都没有就返回空。 */
    public static BigDecimal mean(List<BigDecimal> values) {
        List<BigDecimal> xs = numbers(values);
        if (xs.isEmpty()) {
            return null;
        }
        // 先把所有点加起来
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal x : xs) {
            sum = sum.add(x);
        }
        // 总和 ÷ 个数 = 平均值；留 8 位小数，四舍五入
        return sum.divide(BigDecimal.valueOf(xs.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 估过程波动 σ。
     * 思路：看相邻两点差多少（移动极差），差得大说明过程晃得厉害。
     * 至少要 2 个点，一个点没法比。
     */
    public static BigDecimal sigma(List<BigDecimal> values) {
        List<BigDecimal> xs = numbers(values);
        if (xs.size() < 2) {
            return null;
        }
        // mrSum：所有「相邻差」加起来；mrN：一共有几组相邻差（点数-1）
        BigDecimal mrSum = BigDecimal.ZERO;
        int mrN = 0;
        for (int i = 1; i < xs.size(); i++) {
            // 当前点减前一个点，再取绝对值（涨跌都算晃动）
            // 例：10→12 差 2；12→11 差 1
            mrSum = mrSum.add(xs.get(i).subtract(xs.get(i - 1)).abs());
            mrN++;
        }
        if (mrN == 0) {
            return null;
        }
        // 平均晃多少：差值总和 ÷ 组数。例：差 2、1 → 平均 1.5
        BigDecimal mrBar = mrSum.divide(BigDecimal.valueOf(mrN), SCALE, RoundingMode.HALF_UP);
        // 平均晃动还不是 σ，要除以 d2(1.128) 才换算成标准差
        return mrBar.divide(D2, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按 I-MR 算出三条控制限。
     * 中心线 CL = 均值；上限 UCL = 均值+3σ；下限 LCL = 均值-3σ。
     * 点掉进上下限外面，就认为过程失控。
     */
    public static Limits limits(List<BigDecimal> values) {
        BigDecimal cl = mean(values);
        BigDecimal sig = sigma(values);
        if (cl == null || sig == null) {
            return null;
        }
        return new Limits(
                // 上限：均值往上走 3 个 σ
                cl.add(THREE.multiply(sig)).setScale(SCALE, RoundingMode.HALF_UP),
                // 中心线：就是均值
                cl,
                // 下限：均值往下走 3 个 σ
                cl.subtract(THREE.multiply(sig)).setScale(SCALE, RoundingMode.HALF_UP),
                sig);
    }

    /**
     * 过程能力 Cp：规格窗口有多宽、过程晃得有多小。
     * 公式：(USL-LSL) / (6σ)。规格越宽、σ 越小，Cp 越大。
     * 缺一边规格或 σ 为 0 就算不了。
     */
    public static BigDecimal cp(BigDecimal usl, BigDecimal lsl, BigDecimal sig) {
        if (usl == null || lsl == null || sig == null || sig.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // 规格上下限之间的宽度，除以 6 倍波动
        return usl.subtract(lsl).divide(SIX.multiply(sig), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 过程能力 Cpk：还要看均值有没有往一边偏。
     * 分别算「离上限多远」「离下限多远」，取更小的那个（瓶颈那一侧）。
     * 缺的一侧忽略。
     */
    public static BigDecimal cpk(BigDecimal usl, BigDecimal lsl, BigDecimal mean, BigDecimal sig) {
        if (mean == null || sig == null || sig.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal upper = null;
        BigDecimal lower = null;
        if (usl != null) {
            // 均值离上限还剩几个「3σ」
            upper = usl.subtract(mean).divide(THREE.multiply(sig), SCALE, RoundingMode.HALF_UP);
        }
        if (lsl != null) {
            // 均值离下限还剩几个「3σ」
            lower = mean.subtract(lsl).divide(THREE.multiply(sig), SCALE, RoundingMode.HALF_UP);
        }
        if (upper == null) {
            return lower;
        }
        if (lower == null) {
            return upper;
        }
        // 两边取小的：哪边更紧就听哪边的
        return upper.min(lower);
    }

    /** 把空值扔掉，只留真正有数字的点，免得统计算崩。 */
    private static List<BigDecimal> numbers(List<BigDecimal> values) {
        List<BigDecimal> xs = new ArrayList<>();
        if (values == null) {
            return xs;
        }
        for (BigDecimal v : values) {
            if (v != null) {
                xs.add(v);
            }
        }
        return xs;
    }

    /** 算出来的一套限：上限、中心线、下限，以及当时用的 σ。 */
    public record Limits(BigDecimal ucl, BigDecimal cl, BigDecimal lcl, BigDecimal sigma) {
    }
}
