package com.designmd.platform.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * H2 演示库兼容函数。
 * <p>MySQL 专有的 {@code DATE_FORMAT(ts, '%Y-%m-%d')} 在 H2 中不存在，
 * 该类的 {@link #dateFormat(Timestamp, String)} 通过 H2 的
 * {@code CREATE ALIAS DATE_FORMAT FOR 'com.designmd.platform.demo.H2CompatFunctions.dateFormat'}
 * 注册给 H2 使用，保证统计接口在两个数据库下行为一致。
 * <p>仅在 demo（H2）环境被 H2 调用，默认 MySQL 环境不会执行到这里。
 */
public final class H2CompatFunctions {

    private H2CompatFunctions() {
    }

    /**
     * 兼容 MySQL DATE_FORMAT 的常用占位符：%Y 年、%m 月、%d 日、%H 时、%i 分、%s 秒、%% 百分号。
     */
    public static String dateFormat(Timestamp value, String pattern) {
        if (value == null) {
            return null;
        }
        if (pattern == null) {
            return value.toString();
        }
        LocalDateTime t = value.toLocalDateTime();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '%' && i + 1 < pattern.length()) {
                char f = pattern.charAt(++i);
                switch (f) {
                    case 'Y':
                        sb.append(String.format("%04d", t.getYear()));
                        break;
                    case 'm':
                        sb.append(String.format("%02d", t.getMonthValue()));
                        break;
                    case 'd':
                        sb.append(String.format("%02d", t.getDayOfMonth()));
                        break;
                    case 'H':
                        sb.append(String.format("%02d", t.getHour()));
                        break;
                    case 'i':
                        sb.append(String.format("%02d", t.getMinute()));
                        break;
                    case 's':
                        sb.append(String.format("%02d", t.getSecond()));
                        break;
                    case '%':
                        sb.append('%');
                        break;
                    default:
                        sb.append(f);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
