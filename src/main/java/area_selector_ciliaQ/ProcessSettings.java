package area_selector_ciliaQ;

import java.awt.Font;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

import javax.swing.JFileChooser;
import javax.swing.UIManager;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.WaitForUserDialog;
import ij.io.FileInfo;

/**
 * Stores data about basic processing settings such as type of input (e.g. # of
 * channel), input and output file section etc. Provides GUI to choose settings
 * and methods to implement the methods to retrieves file names and paths and
 * stores the values.
 * 
 * @author sebas
 * 
 */
public class ProcessSettings {

	// add necessary Settings and defaults here

	static final String[] TASKVARIANTS = { "active image in FIJI", "all images open in FIJI", "manual file selection",
			"use list (txt)", "pattern matching (recommended)" };
	String selectedTaskVariant = TASKVARIANTS[2];

	static final String[] bioFormats = { ".tif", "raw microscopy file (e.g. OIB-file)" }; 
	String selectedBioFormat = bioFormats[0]; // set to .tif as channel need to be previously split

	String mainPattern = "_C2_canny3d.tif"; 		// Image with channel to be edited
	String helperPattern = "_C4.tif";		// Image with channel used to set ROIs	
	String suffixEdited = "_ed";			// suffix to save edited channel
	
	
	String posFilePattern = "_C1.tif"; // pattern to be matched in Filename
	String negFilePattern = ""; // pattern to exclude filenames even if pos Pattern was matched
	String negDirPattern = "";	// pattern to exclude files by parent dir

	boolean importRois = false;
	
	boolean resultsToNewFolder = false;
	String resultsDir = ""; // Specifies dir where output files will be saved if they are to be saved no new
							// folder


	// --------------------- Task data

	ArrayList<String> names = new ArrayList<String>();		// files names with ending (e.g. .tif)
	ArrayList<String> paths = new ArrayList<String>();		// paths to parent dir with last file sep ("/")

	private ProcessSettings() {
		super();
	}

	/**
	 * Method to init Processes settings with default options
	 * 
	 * @return
	 * @throws IOException
	 */
	public static ProcessSettings initDefault() throws IOException {
		ProcessSettings inst = new ProcessSettings();
		inst.fileFinder();
		return inst;
	}

	/**
	 * Constructs new Object and triggers a GD for the user
	 * 
	 * @return User-chosen Processing Settings
	 * @throws Exception
	 */
	public static ProcessSettings initByGD(String pluginName, String pluginVersion) throws Exception {

		ProcessSettings inst = new ProcessSettings(); // returned instance of ImageSettingClass

		final Font headingFont = new Font("Sansserif", Font.BOLD, 14);
		final Font textFont = new Font("Sansserif", Font.PLAIN, 14);

		GenericDialog gd = new GenericDialog(pluginName + " - Image Processing Settings");
		gd.setInsets(0, 0, 0);
		gd.addMessage(pluginName + " - Version " + pluginVersion + " (© 2020 Sebastian Rassmann)", headingFont);
		gd.addMessage("Insert Processing settings", textFont);

		// Change as necessary
		gd.setInsets(0, 0, 0);
		gd.addChoice("File selection method ", TASKVARIANTS, inst.selectedTaskVariant);
		gd.addStringField("Enter pattern of file containing the channel for editing", inst.mainPattern, 16);
		gd.addStringField("Enter pattern of file containing channel to define ROIs", inst.helperPattern, 16);
		gd.addStringField("Enter suffix for edited file", inst.suffixEdited, 16);
		gd.addCheckbox("Use existing sets of Rois", inst.importRois);
		gd.addCheckbox("Output to new Folder", inst.resultsToNewFolder);

		// show Dialog-----------------------------------------------------------------
		gd.showDialog();

		// read and process variables--------------------------------------------------
		inst.selectedTaskVariant = gd.getNextChoice();
		inst.mainPattern = gd.getNextString();
		inst.helperPattern = gd.getNextString();
		inst.suffixEdited = gd.getNextString();
		inst.importRois = gd.getNextBoolean();
		inst.resultsToNewFolder = gd.getNextBoolean();

		if(gd.wasCanceled()) throw new Exception("GD canceled by user");

		inst.fileFinder();

		return inst;
	}

	/**
	 * provides method to choose files and stores the retrieved informations, needs
	 * to be called before going on with processing.
	 */
	void fileFinder() throws IOException {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {

		}

		if (this.selectedTaskVariant == TASKVARIANTS[0]) { // only one image open
			if (WindowManager.getIDList() == null) {
				new WaitForUserDialog("Plugin canceled - no image open in FIJI!").show();
				throw new IOException();
			} else {
				FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
				this.names.add(info.fileName); // get name
				this.paths.add(info.directory); // get directory
			}
		} else if (this.selectedTaskVariant == TASKVARIANTS[1]) { // select files individually
			if (WindowManager.getIDList() == null) {
				new WaitForUserDialog("Plugin canceled - no image open in FIJI!").show();
				throw new IOException();
			}
			int IDlist[] = WindowManager.getIDList();
			if (IDlist.length == 1) {
				selectedTaskVariant = TASKVARIANTS[0];
				FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
				names.add(info.fileName); // get name
				paths.add(info.directory); // get directory
			} else {
				for (int i = 0; i < IDlist.length; i++) {
					FileInfo info = WindowManager.getImage(IDlist[i]).getOriginalFileInfo();
					names.add(info.fileName); // get name
					paths.add(info.directory); // get directory
				}
			}
		} else if (this.selectedTaskVariant == TASKVARIANTS[2]) {
			OpenFilesDialog od = new OpenFilesDialog(this);
			od.setLocation(0, 0);
			od.setVisible(true);

			od.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowClosing(WindowEvent winEvt) {
					return;
				}
			});

			// Waiting for od to be done
			while (od.done == false) {
				try {
					Thread.currentThread().sleep(50);
				} catch (Exception e) {
				}
			}
			for (File f : od.filesToOpen) {
				names.add(f.getName());
				paths.add(f.getParent() + System.getProperty("file.separator"));
			}
		} else if (this.selectedTaskVariant == TASKVARIANTS[3]) {
			readFilesFromTxt(System.getProperty("user.dir"));
		} else if (this.selectedTaskVariant == TASKVARIANTS[4]) {
			matchPattern(System.getProperty("user.dir"));
		}
	}

	/**
	 * Inits the names and paths list with the files found in the specified txt-file
	 * 
	 * @param path path to start File Selector
	 * @throws IOException
	 * @throws IOException if txt file not found
	 */
	private void readFilesFromTxt(String path) throws IOException {
//		String txtPath = System.getProperty("user.dir");
		boolean validInput = false;
		while (!validInput) { // wait for valid input
			path = choosePathTxt("Choose txt containing paths of files to process", path);
			if (path.contains(".txt"))
				validInput = true;
		}
		File file = new File(path);
		BufferedReader br = new BufferedReader(new FileReader(file));
		String s = "";
		while ((s = br.readLine()) != null) {
			names.add(s.substring(s.lastIndexOf(System.getProperty("file.separator")) + 1));
			paths.add(s.substring(0, s.lastIndexOf(System.getProperty("file.separator")) + 1));
		}
		br.close();
	}

	/**
	 * choose path to dir
	 * 
	 * @param message
	 * @param defaultpath
	 * @return
	 */
	public static String choosePathTxt(String message, String defaultpath) {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.setMultiSelectionEnabled(false);
		fc.setCurrentDirectory(new File(defaultpath));
		if (fc.showDialog(fc, message) == JFileChooser.APPROVE_OPTION) {
//		   System.out.println(fc.getSelectedFile().getAbsoluteFile());
		}
		String selectedpath = fc.getSelectedFile().getPath();
		return selectedpath;
	}

	/**
	 * implements string pattern matching to search in all sub directories of
	 * specified root directory for files to process.
	 * 
	 * See https://www.vogella.com/tutorials/JavaRegularExpressions/article.html for
	 * regex notation in java
	 * 
	 * @param path    path to directory where the search starts
	 * @param pattern regex to be matched in filenames
	 * @return full paths of files matching requirements in the specified dir
	 */
	private void matchPattern(String rootPath) throws IOException {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setMultiSelectionEnabled(true);
		fc.setCurrentDirectory(new File(rootPath));
		fc.showDialog(fc, "Choose directory to start pattern matching");

		Stack<File> q = new Stack<File>();
		for(File f : fc.getSelectedFiles()) {
			q.push(f);
		}
		
		patternMatchingGD();		// request User input as params for pattern matching
		File[] fid; // Files in Dir
		while (!q.isEmpty()) {
			fid = q.pop().listFiles();
			for (File f : fid) { // loop through files in dir
				if (f.isDirectory() && !f.getName().matches(this.negDirPattern)) {
					q.push(f); // add to queue if f is a dir and negDirPattern can't be matched
				}
				else if (f.getName().matches(this.posFilePattern) && !f.getName().matches(this.negFilePattern)) { 
					// add to file list if posPattern matches and negative Pattern doesn't
					this.names.add(f.getName());
					this.paths.add(f.getParent() + System.getProperty("file.separator"));
				}
			}
		}
		return;
	}
	
	/**
	 * GD requesting user input for Pattern matching and formatting input data
	 * @throws IOException
	 */
	private void patternMatchingGD() throws IOException {
		boolean posFileInputAsRegex = false, negFileInputAsRegex = false, negDirInputAsRegex = false;
		GenericDialog gd = new GenericDialog("Insert pattern matching parameters:");
		
		gd.addCheckbox("Input as Regex", posFileInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern to be matched in filenames", this.mainPattern, 16);
		
		gd.addCheckbox("Input as Regex", negFileInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern in filenames to exclude files", this.negFilePattern, 16);
		
		gd.addCheckbox("Input as Regex", negDirInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern in parent directories to exclude files", this.negDirPattern, 16);
		
		gd.showDialog();

		posFileInputAsRegex = gd.getNextBoolean();
		this.posFilePattern = gd.getNextString();
		negFileInputAsRegex = gd.getNextBoolean();
		this.negFilePattern = gd.getNextString();
		negDirInputAsRegex = gd.getNextBoolean();
		this.negDirPattern = gd.getNextString();
		if (gd.wasCanceled()) {
			throw new IOException("Pattern matching failed");
		}

		if (!posFileInputAsRegex) {
			this.posFilePattern = transformStringToRegex(this.posFilePattern);
		}	
		if (!negFileInputAsRegex) {
			this.negFilePattern = transformStringToRegex(this.negFilePattern);
		}
		if (!negDirInputAsRegex) {
			this.negDirPattern = transformStringToRegex(this.negDirPattern);
		}

	}
	
	public static void main (String args[]) throws IOException {
		ProcessSettings p = new ProcessSettings();
		p.posFilePattern = ".*IMCD3.canny3d\\.tif"; // pattern to be matched in Filename
		p.negFilePattern = ""; // pattern to exclude filenames even if pos Pattern was matched
		p.negDirPattern = "742";	// pattern to exclude files by parent dir
		p.matchPattern("F:\\");
		for(int i = 0; i < p.names.size(); i++) {
			System.out.println(p.paths.get(i)+p.names.get(i) );
		}
		System.out.println("done!");
	}
	
	/**
	 * @param pattern Simple String pattern to be matched
	 * @return Regex allowing all characters before and after the input Pattern
	 */
	static String transformStringToRegex(String pattern) {
		String s = "";
		if (pattern.length() != 0) s = ".*" + pattern.replace(".", "\\.") + ".*";
		return s;
	}
	
	/**
	 * Wraps functionality of opening Images in IJ depending on the chosen file format
	 * @param path path to file to be opened
	 * @return reference of opened ImagePlus
	 */
	public ImagePlus openImage(String path) {
		ImagePlus imp;
		if (this.selectedBioFormat.equals(ProcessSettings.bioFormats[0])) {
			imp = IJ.openImage(path);
		} else {
			IJ.run("Bio-Formats", "open=[" + path
					+ "] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
			imp = WindowManager.getCurrentImage();
		}
		return imp;
	}

	public int getNOfTasks() {
		return this.names.size();
	}

	/**
	 * 
	 * @return task list as Array, use to init {@link ProgressDialog}
	 */
	public String[] toArray() {
		String[] s = new String[this.getNOfTasks()];
		for (int i = 0; i < this.getNOfTasks(); i -= -1) {
			s[i] = this.names.get(i);
		}
		return s;
	}

	/**
	 * Returns the dir where the output should be stored (dir of input image or
	 * fixed dir)
	 */
	public String getOutputDir(int taskIndex) {
		return this.resultsToNewFolder ? this.resultsDir : this.paths.get(taskIndex);
	}

	public void selectOutputDir() {
		if (this.resultsToNewFolder) {
			String path = System.getProperty("user.dir");
			if (this.paths.size() != 0)  {
				path = this.paths.get(0);
			}
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fc.setMultiSelectionEnabled(false);
			fc.setCurrentDirectory(new File(path));
			fc.showDialog(fc, "Select Directory for Output");
			this.resultsDir = fc.getSelectedFile().getPath() + System.getProperty("file.separator");
		}
	}
}
