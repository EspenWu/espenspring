package EspenException;

/**
 * @author WuPeng
 * @Description 自定义异常
 * @Date 2021/4/1
 * @Time 17:36
 * @Version 1.0
 **/
public class TipException extends RuntimeException{

    public TipException(String msg){
        super(msg);
    }
}
