package com.razukalias.jmeter.dataengine;

import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class WorkbookReader {
    public WorkbookModel read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new FileNotFoundException("Workbook not found: " + path);
        try (InputStream in = Files.newInputStream(path); Workbook wb = WorkbookFactory.create(in)) {
            WorkbookModel model = new WorkbookModel();
            Sheet config = sheet(wb, "config");
            Map<String,Integer> ch = headers(config);
            require(ch, "testcaseindex", "config"); require(ch, "testcase", "config"); require(ch, "stepname", "config");
            for (Row r : dataRows(config)) {
                if (blank(r)) continue;
                WorkbookModel.Step s = new WorkbookModel.Step();
                s.testcaseIndex = integer(ch, r, "testcaseindex", r.getRowNum());
                s.testcase = text(ch,r,"testcase"); s.name=text(ch,r,"stepname");
                s.sequence = r.getRowNum();
                s.type=text(ch,r,"steptype","HTTP").toUpperCase(Locale.ROOT);
                s.enabled=bool(ch,r,"enabled",true); s.template=text(ch,r,"templetsource");
                s.stepConfig=text(ch,r,"stepconfig","{}"); s.url=text(ch,r,"targeturl");
                s.verb=text(ch,r,"verb","POST").toUpperCase(Locale.ROOT);
                s.contentType=text(ch,r,"contenttype","application/json"); s.waitMs=Math.max(0,integer(ch,r,"wait",0));
                s.expectedStatus=text(ch,r,"expectedstatus"); s.assertEnabled=bool(ch,r,"assert",bool(ch,r,"assertenabled",true));
                for (String e : split(text(ch,r,"environments"))) { s.environments.add(e); model.environments.add(e); }
                for (Map.Entry<String,Integer> e: ch.entrySet()) if (e.getKey().startsWith("header.")) s.headers.put(e.getKey().substring(7), text(ch,r,e.getKey()));
                WorkbookModel.Testcase tc=model.testcases.computeIfAbsent(s.testcaseIndex,k->{WorkbookModel.Testcase x=new WorkbookModel.Testcase();x.index=k;x.name=s.testcase;return x;});
                tc.steps.add(s);
            }
            for (WorkbookModel.Testcase tc:model.testcases.values()) tc.steps.sort(Comparator.comparingInt(x -> x.sequence));
            Sheet request=sheet(wb,"request"); Map<String,Integer> rh=headers(request);
            require(rh,"testcaseindex","request"); require(rh,"testcase","request"); require(rh,"dataid","request");
            for (Row r:dataRows(request)) {
                if(blank(r))continue; WorkbookModel.DataRow d=new WorkbookModel.DataRow();
                d.testcaseIndex=integer(rh,r,"testcaseindex",r.getRowNum()); d.testcase=text(rh,r,"testcase"); d.dataId=text(rh,r,"dataid"); d.excelRow=r.getRowNum()+1;
                for(Map.Entry<String,Integer> e:rh.entrySet()) { String k=e.getKey(); if(Set.of("testcaseindex","testcase","dataid","dontrun").contains(k))continue; String value=text(rh,r,k); d.variables.put(k,value); int dot=k.indexOf('.'); if(dot>0)d.variables.putIfAbsent(k.substring(dot+1),value); }
                model.rows.put(d.testcaseIndex+"|"+d.dataId,d);
            }
            Sheet response=sheet(wb,"response"); Map<String,Integer> ah=headers(response);
            if(response!=null) for(Row r:dataRows(response)) { if(blank(r))continue; int idx=integer(ah,r,"testcaseindex",r.getRowNum()); String data=text(ah,r,"dataid"); String key=idx+"|"+data;
                for(Map.Entry<String,Integer> e:ah.entrySet()) { String h=e.getKey(); if(Set.of("testcaseindex","testcase","dataid","extractvariable").contains(h))continue; String cell=text(ah,r,h); String verb="eq"; String header=h;
                    if(header.startsWith("{")){int p=header.indexOf('}');if(p>1){verb=header.substring(1,p);header=header.substring(p+1);}}
                    int dot=header.indexOf('.'); if(dot<=0)continue; if(cell.startsWith("{")){int p=cell.indexOf('}');if(p>1){verb=cell.substring(1,p);cell=cell.substring(p+1);}}
                    if(!cell.isBlank() || Set.of("empty","notempty","exists","notexists").contains(verb)) { WorkbookModel.Assertion a=new WorkbookModel.Assertion(); a.step=header.substring(0,dot);a.path=header.substring(dot+1);a.verb=verb.toLowerCase(Locale.ROOT);a.expected=cell;model.assertions.computeIfAbsent(key,k->new ArrayList<>()).add(a); }
                }
            }
            return model;
        } catch (Exception e) { if(e instanceof IOException) throw (IOException)e; throw new IOException("Workbook validation failed: "+e.getMessage(),e); }
    }
    private static Sheet sheet(Workbook wb,String n){for(Sheet s:wb)if(s.getSheetName().equalsIgnoreCase(n))return s; if(n.equalsIgnoreCase("response"))return null;throw new IllegalArgumentException("Required worksheet missing: "+n);}
    private static Map<String,Integer> headers(Sheet s){Map<String,Integer> m=new LinkedHashMap<>(); if(s==null)return m; Row r=s.getRow(s.getFirstRowNum());for(Cell c:r) m.put(c.getStringCellValue().trim().toLowerCase(Locale.ROOT),c.getColumnIndex());return m;}
    private static Iterable<Row> dataRows(Sheet s){List<Row> out=new ArrayList<>();if(s!=null)for(int i=s.getFirstRowNum()+1;i<=s.getLastRowNum();i++)out.add(s.getRow(i));return out;}
    private static String text(Map<String,Integer> h,Row r,String k){return text(h,r,k,"");}
    private static String text(Map<String,Integer> h,Row r,String k,String d){Integer i=h.get(k.toLowerCase(Locale.ROOT));if(i==null)return d;Cell c=r.getCell(i);if(c==null)return d;DataFormatter f=new DataFormatter();return f.formatCellValue(c).trim();}
    private static int integer(Map<String,Integer> h,Row r,String k,int d){try{return Integer.parseInt(text(h,r,k));}catch(Exception e){return d;}}
    private static boolean bool(Map<String,Integer> h,Row r,String k,boolean d){String v=text(h,r,k);return v.isBlank()?d:Boolean.parseBoolean(v);}
    private static boolean blank(Row r){if(r==null)return true;for(Cell c:r)if(c!=null&&!new DataFormatter().formatCellValue(c).isBlank())return false;return true;}
    private static String[] split(String s){return s==null||s.isBlank()?new String[0]:s.split("[,|]");}
    private static void require(Map<String,Integer> h,String k,String s){if(!h.containsKey(k))throw new IllegalArgumentException("Worksheet "+s+" requires column "+k);}
}
