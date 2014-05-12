/* 
 * Copyright (c) 2010 ChiaHung Lin
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package kilim.aio;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AioFactory{
    protected static ConcurrentMap<String, Object> cache = 
        new ConcurrentHashMap<String, Object>();

    public static AsynchronousOperationProcessor createProcessor(
            Class<? extends AsynchronousOperationProcessor> p){
        Object data = cache.get(p.getName());
        if(null == data){
            try{
                AsynchronousOperationProcessor aop = p.newInstance();
                Object prev = cache.putIfAbsent(p.getName(), aop);
                if(null == prev)  data = aop;
            }catch(Exception e){ e.printStackTrace();}
        }
        return (AsynchronousOperationProcessor)data;
    }

    public static AsynchronousOperationProcessor createDefaultProcessor(){
        return createProcessor(DefaultProcessor.class);
    }

    public static CompletionDispatcher<Act> createProactor(
            Class<? extends CompletionDispatcher> d){
       Object data = cache.get(d.getName());
        if(null == data){
            try{
                CompletionDispatcher cd = d.newInstance();
                Object prev = cache.putIfAbsent(d.getName(), cd);
                if(null == prev)  data = cd;
            }catch(Exception e){ e.printStackTrace();}
        }
        return (CompletionDispatcher)data; 
    }

    public static CompletionDispatcher<Act> createDefaultProactor(){
        return createProactor(DefaultProactor.class);
    }
  
}
