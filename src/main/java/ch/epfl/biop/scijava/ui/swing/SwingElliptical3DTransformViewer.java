package ch.epfl.biop.scijava.ui.swing;

import bdv.util.Elliptical3DTransform;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.viewer.EasySwingDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Plugin(type = DisplayViewer.class)
public class SwingElliptical3DTransformViewer extends
        EasySwingDisplayViewer<Elliptical3DTransform> {

    public SwingElliptical3DTransformViewer()
    {
        super( Elliptical3DTransform.class );
    }

    @Override
    protected boolean canView(Elliptical3DTransform elliptical3DTransform) {
        return true;
    }

    @Override
    protected void redoLayout() {

    }

    @Override
    protected void setLabel(String s) {

    }

    @Override
    protected void redraw() {
        Map<String, Double> params = e3Dt.getParameters();
        paramsUI.keySet().forEach(k -> {
            paramsUI.get(k).setValue(params.get(k));
        });
    }

    Elliptical3DTransform e3Dt;

    JPanel panelInfo;
    JLabel nameLabel;
    JTextArea textInfo;

    Map<String, Double> paramsTransfo;
    Map<String, DoubleValueSwingSetLog> paramsUI;

    @Override
    protected JPanel createDisplayPanel(Elliptical3DTransform elliptical3DTransform) {
        e3Dt = elliptical3DTransform;
        paramsTransfo = e3Dt.getParameters();
        paramsUI = new LinkedHashMap<>();

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panelInfo = new JPanel();
        panelInfo.setLayout(new GridLayout(9,1));
        panel.add(panelInfo, BorderLayout.CENTER);
        nameLabel = new JLabel("Elliptical transform name");
        JTextField nameField = new JTextField();
        JPanel paneName = new JPanel();
        paneName.setLayout(new GridLayout(1,2));
        paneName.add(nameLabel);
        paneName.add(nameField);
        nameField.setEditable(true);
        nameField.setText(e3Dt.getName());
        nameField.addActionListener((e) -> e3Dt.setName(nameField.getText()));
        panel.add(paneName, BorderLayout.NORTH);
        textInfo = new JTextArea();
        textInfo.setEditable(false);

        paramsTransfo.keySet().forEach(k -> {
            paramsUI.put(k, new DoubleValueSwingSetLog(k, paramsTransfo.get(k), (v) -> e3Dt.setParameters(k,v)));
            panelInfo.add(paramsUI.get(k).getPanel());
        });

        panel.setPreferredSize(new Dimension(500, 500));

        this.redraw();
        return panel;
    }


    class DoubleValueSwingSetLog {
        JSlider sliderLog;
        JTextField valueTF;
        JLabel labelName;
        JPanel pane;

        Double value, valueOld;

        Consumer<Double> vChanged;

        public DoubleValueSwingSetLog(String name, Double v, Consumer<Double> valueChanged) {
            vChanged = valueChanged;
            sliderLog = new JSlider();
            valueOld = new Double(0);
            valueOld = v;
            sliderLog.setMinimum(-100); // -100 -> /10
            sliderLog.setMaximum(100);  // +100 -> *10
            sliderLog.setValue(0);
            sliderLog.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent event) {
                    if (!sliderLog.getValueIsAdjusting()) {
                        sliderLog.setValue(0);
                        valueOld=value;
                    } else {
                        setValue(valueOld*Math.pow((double)10,(double)sliderLog.getValue()/(double)100));
                    }
                }
            });
            valueTF = new JTextField();
            valueTF.addActionListener(e -> {
                try {
                    Double d = Double.valueOf(valueTF.getText());
                    setValue(d);
                    valueOld=d;
                } catch (Exception exception) {
                    valueTF.setText(Double.toString(valueOld));
                };
            });

            labelName = new JLabel();
            labelName.setText(name);
            value=v;
            this.setValue(value);
            pane = new JPanel();
            pane.setLayout(new GridLayout(1,3));
            pane.add(labelName);
            pane.add(valueTF);
            pane.add(sliderLog);
        }

        void setValue(Double v) {
            value = v;
            valueTF.setText(Double.toString(v));
            vChanged.accept(v);
        }

        public JPanel getPanel() {
            return pane;
        }

    }

}
