package com.courseqa.common;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
@Component
public class CourseLocks {
    private final ConcurrentHashMap<UUID,ReentrantLock> locks=new ConcurrentHashMap<>();
    public <T>T withLock(UUID courseId,Supplier<T> operation) {
        var lock=locks.computeIfAbsent(courseId,k->new ReentrantLock());lock.lock();
        try{return operation.get();}finally{lock.unlock();}
    }
}
