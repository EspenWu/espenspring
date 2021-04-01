package v1;

import EspenException.TipException;
import com.annotation.EspenAutowired;
import com.annotation.EspenController;
import com.annotation.EspenRequestMapping;
import com.annotation.EspenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author WuPeng
 * @Description 手写spring (ioc、di、mvc)
 * @Date 2021/3/31
 * @Time 16:48
 * @Version 1.0
 **/
@Slf4j
public class DispacherServlet extends HttpServlet {
    /**
     * 配置文件
     */
    private static final Properties CONTEXT_CONFIG = new Properties();
    /**
     * 享元模式
     */
    private static final List<String> CLASS_NAMES = new ArrayList<String>();
    /**
     * IoC容器，key默认是类名首字母小写，value就是对应的实例对象
     */
    private static final Map<String, Object> IOC = new HashMap<String, Object>();
    /**
     * url与方法映射
     */
    private static final Map<String, Method> HANDLER_MAPPING = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp){
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp){
       log.info("doDispacher()...");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、扫描相关的类
        doScanner(CONTEXT_CONFIG.getProperty("scanPackage"));

        //3、初始化扫描到的类，并且将它们放入到ICO容器之中
        doInstance();

        //4、完成依赖注入
        doAutowired();

        //5、初始化HandlerMapping
        initHandlerMapping();

        log.info("EspenSpring framework is init.");
    }

    private void initHandlerMapping() {
        if (IOC.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : IOC.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(EspenController.class)) {
                return;
            }

            //获取类上的url /demo
            StringBuilder baseUrl = new StringBuilder();
            if (clazz.isAnnotationPresent(EspenRequestMapping.class)) {
                baseUrl = baseUrl.append(clazz.getAnnotation(EspenRequestMapping.class).value());
            }
            //获取方法上的url /queryUser
            for (Method method : clazz.getMethods()) {
                if (method.isAnnotationPresent(EspenRequestMapping.class)) {
                    // /demo//queryUser
                    baseUrl = baseUrl.append("/").append(method.getAnnotation(EspenRequestMapping.class).value());
                }

                // //demo//queryUser->/demo/queryUser
                StringBuilder url =new StringBuilder("/");
                url.append(baseUrl);
                HANDLER_MAPPING.put(url.toString().replaceAll("/+","/"),method);
            }
        }
    }

    /**
     * 自动注入
     */
    private void doAutowired() {
        if (IOC.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : IOC.entrySet()) {

            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for (Field field : fields) {

                if (!field.isAnnotationPresent(EspenAutowired.class)) {
                    continue;
                }

                EspenAutowired autowired = field.getAnnotation(EspenAutowired.class);

                String beanName = autowired.value().trim();

                if ("".equals(beanName)) {
                    //field.getType().getName() 获取字段的类型
                    beanName = field.getType().getName();
                }

                //暴力访问
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), IOC.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化ioc容器，类名首字母小写，类实例（K,V）
     */
    private void doInstance() {
        if (CLASS_NAMES.isEmpty()) {
            return;
        }
        for (String className : CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(EspenController.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    Object instance = clazz.newInstance();
                    IOC.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(EspenService.class)) {
                    String beanName = clazz.getAnnotation(EspenService.class).value();
                    if (StringUtils.isBlank(beanName)) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    IOC.put(beanName, instance);
                    //3、如果是接口
                    //判断有多少个实现类，如果只有一个，默认就选择这个实现类
                    //如果有多个，只能抛异常
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (IOC.containsKey(i.getName())) {
                            throw new TipException("The" + i.getName() + "is exist！");
                        }
                        IOC.put(toLowerFirstCase(i.getSimpleName()), instance);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 将类名首字母转小写
     *
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char a = 'A';
        char z = 'Z';
        int sub = 32;
        char[] chars = simpleName.toCharArray();
        if (chars[0] >= a && chars[0] <= z) {
            chars[0] += sub;
        }
        return String.valueOf(chars);
    }

    /**
     * 读取配置文件并扫描需要ioc的类
     *
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        String basePath = scanPackage.replace(".", "/");
        File file = new File(basePath);
        for (File listFile : file.listFiles()) {
            //com/base/controller
            if (listFile.isDirectory()) {
                doScanner(basePath + "/" + listFile.getName());
            }
            if (listFile.getName().endsWith(".class")) {
                //全类名 = 包名.类名
                String className = basePath + "." + listFile.getName().split("\\.")[0];
                CLASS_NAMES.add(className);
            }
        }

    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream configFile = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            /**
             * ResourceBundle与Properties
             * 使用Properties类来读取properties文件时文件可以与代码不同包下，这种更符合实际场景
             */
            CONTEXT_CONFIG.load(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (configFile != null) {
                try {
                    configFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
