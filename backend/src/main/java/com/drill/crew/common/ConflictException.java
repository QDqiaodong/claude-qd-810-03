package com.drill.crew.common;

/**
 * 并发冲突异常：交接采用乐观锁，两名负责人同时推进同一钻孔时，
 * 后提交者持有的 lockVersion 已过期 —— 必须看到数据已变化并重新检查（409）。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
