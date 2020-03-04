package area_selector_ciliaQ;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.RoiManager;

public class Processing {

	/**
	 * Wraps the logic and real processing of the generated plugin.
	 * 
	 * @param path      path to dir of the image
	 * @param name      name of the image - path/image should be exact path of the
	 *                  image to be processed
	 * @param impIn     ImagePlus to be processed - can be null if not used
	 * @param outputDir Path to dir where the output should be saved
	 * @return
	 */

	static boolean doProcessing(String path, String name, String outputDir, ProcessSettings pS, ImageSetting iS,
			ProgressDialog pD) {

		ImagePlus c1 = IJ.openImage(path + name);
		String modPattern = pS.pattern.substring(pS.pattern.lastIndexOf("*") + 1, pS.pattern.length());
		String c2Name = name.replaceAll(modPattern, pS.helperString);
		ImagePlus c2 = IJ.openImage(path + c2Name); // channel used to determine selection
		
		c1.show();
		c2.show();
		
		IJ.run("Merge Channels...", "c1=[" + c1.getTitle() + "] c2=[" + c2.getTitle() + "] create keep ignore");
		ImagePlus merged = IJ.getImage();
		IJ.run(merged, "Z Project...", "projection=[Max Intensity]");
		merged.close();
		merged = IJ.getImage();
		IJ.setTool("freehand");
		IJ.setBackgroundColor(0, 0, 0);
		RoiManager rm = new RoiManager();
		new WaitForUserDialog("Draw ROIs around desired area and press Ctrl + T\n"
				+ "to add selection add to ROI manager.\n" + "Confirm with OK").show();
		rm.deselect();
		rm.runCommand("Combine");
		rm.reset();
		rm.runCommand("Add");
		rm.select(c1, 0);
		IJ.run(c1, "Clear Outside", "stack");
	
		IJ.save(c1, outputDir + name.substring(0, name.lastIndexOf(".tif")) + "_ed.tif");
		IJ.save(merged, outputDir + name.replaceAll(modPattern, "_zProjection.tif"));
		
		c1.close();
		c2.close();
		merged.close();
		rm.close();

		return true;
	}
}
