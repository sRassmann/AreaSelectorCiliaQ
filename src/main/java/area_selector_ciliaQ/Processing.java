package area_selector_ciliaQ;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.RoiManager;

public class Processing {

	/**
	 * Wraps the logic and real processing of the generated plugin.
	 * 
	 * @param path      path to dir of the image
	 * @param name      name of the image - path/image should be exact path of the
	 *                  image to be processed
	 * @param outputDir Path to dir where the output should be saved
	 * @return
	 */

	static boolean doProcessing(String path, String name, String outputDir, ProcessSettings pS, ProgressDialog pD) {

		ImagePlus c1 = IJ.openImage(path + name);
		String c2Name = name.replaceAll(pS.mainPattern, pS.helperPattern);
		ImagePlus c2;
		try{
			c2 = IJ.openImage(path + c2Name); // channel used to determine selection
		} catch (NullPointerException e) {
			return false;
		}

		c1.show();
		c2.show();

		IJ.run("Merge Channels...", "c1=[" + c1.getTitle() + "] c2=[" + c2.getTitle() + "] create keep ignore");
		ImagePlus merged = IJ.getImage();
		merged.show();
		c1.hide();
		c2.hide();
		ImagePlus zProj = (ImagePlus) merged.clone();
		IJ.run(zProj, "Z Project...", "projection=[Max Intensity]");
		zProj = IJ.getImage();
		IJ.setTool("freehand");
		IJ.setBackgroundColor(0, 0, 0);
		RoiManager rm = new RoiManager();
		if (rm.getCount() != 0) {
			rm.runCommand(zProj, "Show All");
		}
		
		File roisFile = new File(path + name.replace(pS.mainPattern, "_Rois.zip"));
		if (!pS.importRois || !roisFile.exists()) {
			if(pS.importRois || !roisFile.exists()) {
				IJ.showMessage("Rois for file " + name + " not found - please draw the Rois");
			}
			new WaitForUserDialog("Draw ROIs around desired area and press Ctrl + T\n"
					+ "to add selection add to ROI manager.\n" + "Confirm with OK").show();
		}
		else {
			rm.runCommand("Open", roisFile.getAbsolutePath());
			merged.hide();
		} 
		if (rm.getCount() == 0) { // no selection made
			c1.close();
			c2.close();
			merged.close();
			zProj.close();
			rm.close();
			return false;
		}
		if (rm.getCount() > 1) {
			rm.runCommand("Save", outputDir + name.replace(pS.mainPattern, "_Rois.zip"));
			rm.deselect();
			rm.runCommand("Combine");
			rm.reset();
			rm.runCommand("Add");
		}
		rm.select(c1, 0);
		IJ.run(c1, "Clear Outside", "stack");
		rm.deselect();
		Roi roisCombined = rm.getRoi(0);
		rm.runCommand(c1, "Delete");

		IJ.run(c1, "Remove Overlay", "");
		IJ.save(c1, outputDir + name.substring(0, name.lastIndexOf(".tif")) + pS.suffixEdited + ".tif");

		rm.addRoi(roisCombined);
		rm.select(zProj, 0);
		IJ.save(zProj, outputDir + name.replaceAll(pS.mainPattern, "_zProjection.tif"));

		c1.close();
		c2.close();
		merged.close();
		zProj.close();
		rm.close();

		return true;
	}
}