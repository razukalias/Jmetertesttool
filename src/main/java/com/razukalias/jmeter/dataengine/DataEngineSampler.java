package com.razukalias.jmeter.dataengine;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.jmeter.samplers.*;
import org.apache.jmeter.threads.JMeterContextService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DataEngineSampler extends AbstractSampler {
    public static final String WORKBOOK="workbook", INSTANCE="instance", ENVIRONMENT="environment", TESTCASES="testcases", ROWS="rows", TEMPLATES="templates";
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    public DataEngineSampler(){setName("DataEngine Excel Sequential");}
    public String getWorkbook(){return getPropertyAsString(WORKBOOK,"");} public void setWorkbook(String v){setProperty(WORKBOOK,v);}
    public String getInstance(){return getPropertyAsString(INSTANCE,"");} public void setInstance(String v){setProperty(INSTANCE,v);}
    public String getEnvironment(){return getPropertyAsString(ENVIRONMENT,"");} public void setEnvironment(String v){setProperty(ENVIRONMENT,v);}
    public String getTestcases(){return getPropertyAsString(TESTCASES,"*");} public void setTestcases(String v){setProperty(TESTCASES,v);}
    public String getRows(){return getPropertyAsString(ROWS,"*");} public void setRows(String v){setProperty(ROWS,v);}
    public String getTemplates(){return getPropertyAsString(TEMPLATES,"Templates");} public void setTemplates(String v){setProperty(TEMPLATES,v);}

    @Override public SampleResult sample(Entry ignored){
        SampleResult parent=new SampleResult(); parent.setSampleLabel(getName()); parent.sampleStart();
        try {
            Path workbook=Path.of(getWorkbook()).toAbsolutePath().normalize(); WorkbookModel model=new WorkbookReader().read(workbook);
            Set<Integer> selected=parseIndexes(getTestcases()), selectedRows=parseIndexes(getRows()); int count=0;
            for(WorkbookModel.Testcase tc:model.testcases.values()) {
                if(!selected.isEmpty()&&!selected.contains(tc.index)) continue;
                for(WorkbookModel.DataRow row:model.rows.values()) {
                    if(row.testcaseIndex!=tc.index || (!selectedRows.isEmpty()&&!selectedRows.contains(row.excelRow))) continue;
                    count++; SampleResult child=runRow(model,tc,row,getEnvironment(),workbook.getParent()); parent.addSubResult(child);
                    if(!child.isSuccessful()){parent.setSuccessful(false);parent.setResponseMessage(child.getResponseMessage());return parent;}
                }
            }
            parent.setSuccessful(true); parent.setResponseMessage("Executed "+count+" testcase-row item(s) sequentially");
        } catch(Exception e) { parent.setSuccessful(false); parent.setResponseMessage(String.valueOf(e.getMessage())); parent.setResponseData(String.valueOf(e),"UTF-8"); stopEntireTest(); }
        return parent;
    }

    private SampleResult runRow(WorkbookModel model,WorkbookModel.Testcase tc,WorkbookModel.DataRow row,String env,Path root)throws Exception {
        SampleResult result=new SampleResult(); result.setSampleLabel(tc.name+" / Row "+row.excelRow); result.sampleStart();
        Map<String,String> vars=new LinkedHashMap<>(row.variables); vars.put("env",env); Map<String,String> requests=new HashMap<>(), responses=new HashMap<>(); TokenResolver resolver=new TokenResolver(); boolean rowSuccessful=true;
        for(WorkbookModel.Step s:tc.steps) {
            if(!s.enabled || (!s.environments.isEmpty()&&!s.environments.stream().anyMatch(x->x.trim().equalsIgnoreCase(env)))) continue;
            if(row.dontrun.contains(s.name.trim().toLowerCase(Locale.ROOT))) continue;
            if(s.waitMs>0) Thread.sleep(s.waitMs); if(s.type.equalsIgnoreCase("WAIT")) continue;
            SampleResult step=new SampleResult(); step.setSampleLabel(tc.name+" / Row "+row.excelRow+" / "+s.name); step.sampleStart();
            try {
                String body=loadBody(s,root,resolver,vars,requests,responses); for(Map.Entry<String,String> v:vars.entrySet()) requests.put(s.name+"."+v.getKey(),v.getValue());
                if(s.type.equalsIgnoreCase("SCRIPT")||s.type.equalsIgnoreCase("GROOVY")) {
                    Binding binding=new Binding(); for(Map.Entry<String,String> v:vars.entrySet()) binding.setVariable(v.getKey(),v.getValue()); Object output=new GroovyShell(binding).evaluate(body); String value=output==null?"":String.valueOf(output); vars.put(s.name+".result",value); responses.put(s.name+".result",value); step.setResponseData(value,"UTF-8"); step.setSuccessful(true); step.sampleEnd(); result.addSubResult(step); continue;
                }
                String url=resolver.resolve(s.url,vars,requests,responses); HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url));
                for(Map.Entry<String,String> h:s.headers.entrySet()) b.header(h.getKey(),resolver.resolve(h.getValue(),vars,requests,responses));
                HttpRequest.BodyPublisher publisher=body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body);
                HttpResponse<String> response=client.send(b.method(s.verb.toUpperCase(Locale.ROOT),publisher).header("Content-Type",s.contentType).build(),HttpResponse.BodyHandlers.ofString());
                step.setResponseCode(String.valueOf(response.statusCode())); step.setResponseData(response.body(),"UTF-8"); boolean httpOk=response.statusCode()>=200&&response.statusCode()<300; step.setSuccessful(httpOk);
                responses.put(s.name+".statusCode",String.valueOf(response.statusCode())); responses.put(s.name+".body",response.body());
                for(WorkbookModel.Assertion a:model.assertions.getOrDefault(tc.index+"|"+row.dataId,List.of())) {
                    if(!a.step.equalsIgnoreCase(s.name)) continue; String actual="statusCode".equalsIgnoreCase(a.path)?String.valueOf(response.statusCode()):TokenResolver.jsonPath(response.body(),a.path); boolean ok=assertion(a.verb,actual,a.expected);
                    SampleResult ar=new SampleResult(); ar.setSampleLabel("Assertion "+a.verb+" "+a.path); ar.setResponseData((actual+" expected "+a.expected),"UTF-8"); ar.setSuccessful(ok); step.addSubResult(ar); if(!ok)step.setSuccessful(false);
                }
                step.sampleEnd(); result.addSubResult(step);
                if(!step.isSuccessful()){rowSuccessful=false;result.setResponseMessage("HTTP status or assertion failure at "+s.name);if(!httpOk){stopEntireTest();result.setSuccessful(false);return result;}}
            } catch(Exception e) {step.setSuccessful(false);step.setResponseMessage(String.valueOf(e.getMessage()));step.sampleEnd();result.addSubResult(step);result.setSuccessful(false);stopEntireTest();return result;}
        }
        result.setSuccessful(rowSuccessful); return result;
    }
    private String loadBody(WorkbookModel.Step s,Path root,TokenResolver resolver,Map<String,String>v,Map<String,String>q,Map<String,String>p)throws Exception {String body=s.stepConfig==null?"":s.stepConfig;if(s.template!=null&&!s.template.isBlank()){Path base=root.resolve(getTemplates()).normalize(),file=base.resolve(s.template).normalize();if(!file.startsWith(base))throw new SecurityException("Template outside configured Templates folder");body=Files.readString(file);}body=resolver.resolve(body,v,q,p);if((s.type.equalsIgnoreCase("GRAPHQL")||s.type.equalsIgnoreCase("CASEQL"))&&!body.trim().startsWith("{")){body="{\"query\":"+quoteJson(body)+"}";}return body;}
    private String quoteJson(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    private boolean assertion(String verb,String actual,String expected){return switch(verb.toLowerCase(Locale.ROOT)){case "contains"->actual.contains(expected);case "notcontains"->!actual.contains(expected);case "startswith"->actual.startsWith(expected);case "endswith"->actual.endsWith(expected);case "notempty","exists"->!actual.isBlank();case "empty","notexists"->actual.isBlank();case "regex"->actual.matches(expected);default->actual.equals(expected);};}
    private void stopEntireTest(){try{if(JMeterContextService.getContext().getEngine()!=null)JMeterContextService.getContext().getEngine().stopTest(false);}catch(Exception ignored){}}
    private static Set<Integer> parseIndexes(String s){Set<Integer> out=new HashSet<>();if(s==null||s.isBlank()||s.equals("*"))return out;for(String x:s.split(","))try{out.add(Integer.parseInt(x.trim()));}catch(Exception ignored){}return out;}
}
