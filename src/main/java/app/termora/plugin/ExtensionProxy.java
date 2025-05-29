package app.termora.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

record ExtensionProxy(Extension extension) implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(ExtensionProxy.class);

    public Extension getProxyedExtension() {
        return (Extension) Proxy.newProxyInstance(extension.getClass().getClassLoader(), extension.getClass().getInterfaces(), this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (extension.getDispatchThread() == DispatchThread.EDT) {
            if (!SwingUtilities.isEventDispatchThread()) {
                if (log.isErrorEnabled()) {
                    log.error("Event Dispatch Thread", new WrongThreadException("Event Dispatch Thread"));
                }
            }
        }
        return method.invoke(extension, args);
    }
}
