package net.strongdesign.desij.gui;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;

import javax.swing.JDialog;

public class STGEditorAbout extends JDialog {
	private static final long serialVersionUID = 7659693520232299217L;
	private Image logo;
	
    public STGEditorAbout(STGEditorFrame frame) {
        super(frame, "About - JDesi", true);        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setSize(400, 300);

    	logo = Toolkit.getDefaultToolkit().getImage("/home/mark/jdesi-logo.gif");    	
    	
    }
    
    protected void paintComponent(Graphics g) {
    	g.drawLine(10, 10, 20, 20);
    	g.drawImage(logo, 0, 0, 400, 300, null);
    }
    

}
