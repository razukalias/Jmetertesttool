package com.razukalias.jmeter.dataengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

public final class DiscoveryMain {
    public static void main(String[] args) throws Exception {
        if(args.length<1){System.err.println("Usage: java ...DiscoveryMain <instances-root>");System.exit(2);}
        Path root=Path.of(args[0]).toAbsolutePath().normalize(); List<Map<String,Object>> instances=new ArrayList<>();
        try(var paths=Files.walk(root,3)){paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")).filter(p->!p.toString().contains("History")).forEach(p->{try{WorkbookModel m=new WorkbookReader().read(p);List<Map<String,Object>> cases=new ArrayList<>();for(WorkbookModel.Testcase tc:m.testcases.values()){List<Integer> rows=new ArrayList<>();for(WorkbookModel.DataRow r:m.rows.values())if(r.testcaseIndex==tc.index)rows.add(r.excelRow);cases.add(Map.of("index",tc.index,"name",tc.name,"rows",rows));}instances.add(Map.of("instance",p.getParent().getFileName().toString(),"workbook",p.toString(),"environments",m.environments,"testcases",cases));}catch(Exception e){System.err.println("Skipping "+p+": "+e.getMessage());}});}
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(instances));
    }
}
