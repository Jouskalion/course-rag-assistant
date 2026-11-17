package com.courseqa.db;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class WriteTransactions {
    private final ReentrantLock lock=new ReentrantLock();private final TransactionTemplate tx;
    public WriteTransactions(PlatformTransactionManager manager){tx=new TransactionTemplate(manager);}
    public <T>T execute(Supplier<T> operation){lock.lock();try{return tx.execute(s->operation.get());}finally{lock.unlock();}}
}
