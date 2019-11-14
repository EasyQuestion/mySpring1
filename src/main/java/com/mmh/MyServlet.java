package com.mmh;

import com.mmh.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class MyServlet extends HttpServlet {

    private Properties configProperties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> iocMap = new HashMap<String,Object>();

    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 Exception,Detail:"+Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        String uri = req.getRequestURI().replace(req.getContextPath(),"").replaceAll("/+","/");
        if(!handlerMapping.containsKey(uri)){
            resp.getWriter().write("404 Not found!!!");
            return;
        }
        Method method = handlerMapping.get(uri);
        Object obj = iocMap.get(getLowerFirstCase(method.getDeclaringClass().getSimpleName()));
        Map<String,String[]> reqParamMap = req.getParameterMap();
        Class[] paramTypes = method.getParameterTypes();
        Object[] paramValues = new Object[paramTypes.length];

        for(int i=0;i<paramTypes.length;i++){
            Class paramType = paramTypes[i];
            if(paramType == HttpServletRequest.class){
                paramValues[i] = req;
            }else if(paramType == HttpServletResponse.class){
                paramValues[i] = resp;
            }else{
               Annotation[] annotations = method.getParameterAnnotations()[i];
               for(Annotation a:annotations){
                   if(a.annotationType() != RequestParam.class){continue;}
                   String paramName = ((RequestParam) a).value();
                   paramValues[i] = Arrays.toString(reqParamMap.get(paramName))
                           .replaceAll("\\[|\\]","");
               }
            }
        }
        try {
            method.invoke(obj,paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描相关的类
        doScanner(configProperties.getProperty("scan.path"));
        //3.初始化扫描到的类，并放入IOC容器中
        doInstance();
        //4.完成依赖注入
        doAutowired();
        //5.初始化HandlerMapping
        doHandlerMapping();

        System.out.println("spring framework is init!");
    }

    private void doHandlerMapping() {
        if(iocMap.isEmpty()){return;}

        try {
            for (Map.Entry<String,Object> entry : iocMap.entrySet()) {
                //获取方法上的路径
                Class clazz = entry.getValue().getClass();
                if(!clazz.isAnnotationPresent(Controller.class)){continue;}

                String baseUrl = "";
                if(clazz.isAnnotationPresent(RequestMapping.class)){
                    RequestMapping requestMapping = (RequestMapping)clazz.getAnnotation(RequestMapping.class);
                    baseUrl = "/"+requestMapping.value();
                }

                //获取所有的public方法
                Method[] methods = clazz.getMethods();

                for(Method m:methods){
                    if(!m.isAnnotationPresent(RequestMapping.class)){continue;}

                    RequestMapping requestMapping = m.getAnnotation(RequestMapping.class);
                    String url = (baseUrl +"/"+requestMapping.value()).replaceAll("/+","/");
                    //将路径与方法对应，放入到handlerMapping中
                    handlerMapping.put(url,m);
                    System.out.println("mapping  "+url+":"+m);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doAutowired(){

        if(iocMap.isEmpty()){return;}

        for(Map.Entry<String,Object> entry:iocMap.entrySet()){

            //获取public/protected/private所有的方法
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for(Field f:fields){
                if(!f.isAnnotationPresent(Autowired.class)){continue;}
                Autowired autowired = (Autowired)f.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = f.getType().getName();
                }
                f.setAccessible(true);
                try {
                    f.set(entry.getValue(),iocMap.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private void doInstance() {

        if(classNames.isEmpty()){return;}

        try {
            for(String className:classNames){

                Class clazz = Class.forName(className);

                if(clazz.isAnnotationPresent(Controller.class)){
                    //获取注解上的值
                    Controller controller = (Controller)clazz.getAnnotation(Controller.class);
                    String beanName = controller.value().trim();
                    if("".equals(beanName)){
                        //获取类名首字母小写
                        beanName = getLowerFirstCase(clazz.getSimpleName());
                    }
                    iocMap.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(Service.class)){
                    Service service = (Service)clazz.getAnnotation(Service.class);
                    String beanName = service.value().trim();
                    if("".equals(beanName)){
                        beanName =  getLowerFirstCase(clazz.getSimpleName());
                    }
                    Object obj = clazz.newInstance();
                    iocMap.put(beanName,obj);

                    //获取接口
                    Class[] interfaces = clazz.getInterfaces();
                    for(Class interfaceClass:interfaces){
                        if(iocMap.containsKey(interfaceClass.getName())){
                            throw new Exception("the '"+interfaceClass.getName()+"' is exists!");
                        }
                        //将接口与对应的实现类对应，放入ioc容器中
                        iocMap.put(interfaceClass.getName(),obj);
                    }
                }else{
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getLowerFirstCase(String className) {
        char[] charArr = className.toCharArray();
        charArr[0] += 32;
        return new String(charArr);
    }

    private void doScanner(String classPath) {
        URL url = this.getClass().getClassLoader().getResource("/"+classPath.replaceAll("\\.","/"));
        File file = new File(url.getFile());
        for(File f:file.listFiles()){
            if(f.isDirectory()){
                doScanner(classPath+"."+f.getName());
            }else{
                if(!f.getName().endsWith(".class")) {continue;}
                String className = classPath + "." + (f.getName().replace(".class", ""));
                classNames.add(className);
            }
        }
    }

    private void doConfig(String configPath) {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(configPath.replace("classpath:",""));
        try {
            configProperties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != in){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
