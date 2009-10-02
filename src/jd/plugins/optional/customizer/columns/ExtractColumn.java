package jd.plugins.optional.customizer.columns;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;

import jd.gui.swing.components.table.JDTableColumn;
import jd.gui.swing.components.table.JDTableModel;
import jd.plugins.optional.customizer.CustomizeSetting;
import jd.plugins.optional.customizer.CustomizerTableModel;

import org.jdesktop.swingx.renderer.JRendererCheckBox;

public class ExtractColumn extends JDTableColumn implements ActionListener {

    private static final long serialVersionUID = 3755976431978837924L;
    private JRendererCheckBox boolrend;
    private JCheckBox checkbox;

    public ExtractColumn(String name, JDTableModel table) {
        super(name, table);
        boolrend = new JRendererCheckBox();
        boolrend.setHorizontalAlignment(JCheckBox.CENTER);
        checkbox = new JCheckBox();
        checkbox.setHorizontalAlignment(JCheckBox.CENTER);
    }

    @Override
    public Object getCellEditorValue() {
        return checkbox.isSelected();
    }

    @Override
    public boolean isEditable(Object obj) {
        return isEnabled(obj);
    }

    @Override
    public boolean isEnabled(Object obj) {
        return ((CustomizeSetting) obj).isEnabled();
    }

    @Override
    public boolean isSortable(Object obj) {
        return false;
    }

    @Override
    public Component myTableCellEditorComponent(JDTableModel table, Object value, boolean isSelected, int row, int column) {
        checkbox.removeActionListener(this);
        checkbox.setSelected(((CustomizeSetting) value).isExtract());
        checkbox.addActionListener(this);
        return checkbox;
    }

    @Override
    public Component myTableCellRendererComponent(JDTableModel table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        boolrend.setSelected(((CustomizeSetting) value).isExtract());
        return boolrend;
    }

    @Override
    public void setValue(Object value, Object object) {
        ((CustomizeSetting) object).setExtract((Boolean) value);
    }

    @Override
    public void sort(Object obj, boolean sortingToggle) {
    }

    public void actionPerformed(ActionEvent e) {
        checkbox.removeActionListener(this);
        this.fireEditingStopped();
    }

    @Override
    public void postprocessCell(Component c, JDTableModel table, Object value, boolean isSelected, int row, int column) {
        if (((CustomizeSetting) value).matches(((CustomizerTableModel) table).getJDTable().getGui().getTestText())) {
            c.setBackground(new Color(204, 255, 170));
        }
    }

}
