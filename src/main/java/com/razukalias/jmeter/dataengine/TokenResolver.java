package com.razukalias.jmeter.dataengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;

public final class TokenResolver {
    private static final Pattern ANGLE=Pattern.compile("<([^<>]+)>");
    private static final Pattern UNDERSCORE=Pattern.compile("_([A-Za-z][A-Za-z0-9_.-]*)_");
    private static final ObjectMapper JSON=new ObjectMapper();
    public String resolve(String input, Map<String,String> vars, Map<String,String> requests, Map<String,String> responses){
        if(input==null)return ""; String s=input;
        s=replace(s,UNDERSCORE,m->vars.getOrDefault(m.group(1),m.group(0)));
        for(int pass=0;pass<5;pass++){Matcher m=ANGLE.matcher(s);StringBuffer b=new StringBuffer();boolean found=false;while(m.find()){found=true;String v=token(m.group(1),vars,requests,responses);m.appendReplacement(b,Matcher.quoteReplacement(v));}m.appendTail(b);s=b.toString();if(!found||!ANGLE.matcher(s).find())break;}
        return s;
    }
    private String token(String t,Map<String,String> vars,Map<String,String> req,Map<String,String> resp){
        String x=t.trim(); if(x.equalsIgnoreCase("guid"))return UUID.randomUUID().toString();
        if(x.toLowerCase(Locale.ROOT).startsWith("guid:"))return UUID.randomUUID().toString().replace("-","").substring(0,Math.min(32,Math.max(1,Integer.parseInt(x.substring(5)))));
        if(x.toLowerCase(Locale.ROOT).startsWith("randomnumber")){int n=6;int p=x.indexOf(':');if(p>0)try{n=Integer.parseInt(x.substring(p+1));}catch(Exception ignored){}StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(ThreadLocalRandom.current().nextInt(10));return b.toString();}
        if(x.toLowerCase(Locale.ROOT).startsWith("datum:")||x.toLowerCase(Locale.ROOT).startsWith("currentdatetime")){String p=x.startsWith("datum:")?x.substring(6):"yyyyMMdd HH:mm:ss";return DateTimeFormatter.ofPattern(p).format(LocalDateTime.now());}
        String low=x.toLowerCase(Locale.ROOT); if(low.startsWith("from_request_"))return lookup(req,x.substring(13)); if(low.startsWith("from_response_"))return lookup(resp,x.substring(14));
        return vars.getOrDefault(x,"<"+x+">");
    }
    private String lookup(Map<String,String> map,String key){if(key.endsWith("_r"))key=key.substring(0,key.length()-2);String exact=map.get(key);if(exact!=null)return exact;int dot=key.indexOf('.');if(dot>0){String step=key.substring(0,dot),path=key.substring(dot+1),body=map.get(step+".body");if(body!=null)return jsonPath(body,path);}return "";}
    private static String replace(String s,Pattern p,java.util.function.Function<Matcher,String> f){Matcher m=p.matcher(s);StringBuffer b=new StringBuffer();while(m.find())m.appendReplacement(b,Matcher.quoteReplacement(f.apply(m)));m.appendTail(b);return b.toString();}
    public static String jsonPath(String body,String path){try{JsonNode n=JSON.readTree(body);for(String p:path.split("\\.")){if(p.contains("[")){int i=p.indexOf('[');String a=p.substring(0,i);int j=p.indexOf(']',i);n=n.path(a).path(Integer.parseInt(p.substring(i+1,j)));}else n=n.path(p);}return n.isMissingNode()?"":n.isValueNode()?n.asText():n.toString();}catch(Exception e){return "";}}
}
