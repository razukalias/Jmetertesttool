package com.razukalias.jmeter.dataengine;

import org.apache.jorphan.gui.JLabeledTextField;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;

public final class DataEngineSamplerGui extends AbstractSamplerGui {
    private final JLabeledTextField workbook=new JLabeledTextField("Workbook:");
    private final JLabeledTextField instance=new JLabeledTextField("Instance:");
    private final JLabeledTextField environment=new JLabeledTextField("Environment:");
    private final JLabeledTextField testcases=new JLabeledTextField("Testcase indexes:");
    private final JLabeledTextField rows=new JLabeledTextField("Excel rows:");
    private final JLabeledTextField templates=new JLabeledTextField("Templates folder:");
    public DataEngineSamplerGui(){super();setLayout(new BorderLayout(8,8));setBorder(makeBorder());JPanel p=new JPanel(new GridLayout(0,1,4,4));p.add(workbook);p.add(instance);p.add(environment);p.add(testcases);p.add(rows);p.add(templates);JButton discover=new JButton("Discover / select workbook items");discover.addActionListener(e->discover());p.add(discover);add(makeTitlePanel(),BorderLayout.NORTH);add(p,BorderLayout.CENTER);}
    private void discover(){JFileChooser c=new JFileChooser();if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path p=c.getSelectedFile().toPath();try{WorkbookModel m=new WorkbookReader().read(p);String[] t=m.testcases.values().stream().map(x->x.index+" - "+x.name).toArray(String[]::new);JList<String> list=new JList<>(t);list.setSelectionInterval(0,Math.max(0,t.length-1));JTextField env=new JTextField(m.environments.stream().findFirst().orElse(""));JTextField row=new JTextField("*");JPanel panel=new JPanel(new BorderLayout(5,5));panel.add(new JScrollPane(list),BorderLayout.CENTER);JPanel bottom=new JPanel(new GridLayout(2,2));bottom.add(new JLabel("Environment"));bottom.add(env);bottom.add(new JLabel("Rows (Excel numbers, * = all)"));bottom.add(row);panel.add(bottom,BorderLayout.SOUTH);int result=JOptionPane.showConfirmDialog(this,panel,"Select DataEngine execution",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(result==JOptionPane.OK_OPTION){workbook.setText(p.toString());environment.setText(env.getText());rows.setText(row.getText());StringBuilder ids=new StringBuilder();for(String x:list.getSelectedValuesList()){if(ids.length()>0)ids.append(',');ids.append(x.substring(0,x.indexOf(' ')));}testcases.setText(ids.toString());}}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Workbook validation failed",JOptionPane.ERROR_MESSAGE);}}
    @Override public String getStaticLabel(){return "DataEngine Excel Sequential";} @Override public String getLabelResource(){return null;} @Override public TestElement createTestElement(){DataEngineSampler s=new DataEngineSampler();modifyTestElement(s);return s;} @Override public void configure(TestElement e){super.configure(e);DataEngineSampler s=(DataEngineSampler)e;workbook.setText(s.getWorkbook());instance.setText(s.getInstance());environment.setText(s.getEnvironment());testcases.setText(s.getTestcases());rows.setText(s.getRows());templates.setText(s.getTemplates());} @Override public void modifyTestElement(TestElement e){e.clear();configureTestElement(e);DataEngineSampler s=(DataEngineSampler)e;s.setWorkbook(workbook.getText());s.setInstance(instance.getText());s.setEnvironment(environment.getText());s.setTestcases(testcases.getText());s.setRows(rows.getText());s.setTemplates(templates.getText());}
}
