// This plugin is a merge of the Time_Stamper plugins from ImageJ and from Tony Collins' plugin collection at macbiophotonics. 
// it aims to combine all the functionality of both plugins and refine and enhance their functionality,
//for instance by adding the preview functionality suggested by Michael Weber.

// It does not know about hyper stacks - multiple channels..... only works as expected for normal stacks.
// That meeans a single channel time series stack. 

// Dan White MPI-CBG , began hacking on 15.04.09

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Toolbar;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class Time_Stamper_Enhanced implements ExtendedPlugInFilter, DialogListener { //, ActionListener {
					// http://rsb.info.nih.gov/ij/developer/api/ij/plugin/filter/ExtendedPlugInFilter.html
					// should use extended plugin filter for preview ability and for stacks!
					// then need more methods: setNPasses(int last-first)  thats the number of frames to stamp.
					// showDialog method needs  another argument:  PlugInFilterRunner pfr
					// also need Dialog listener and Action listener to listen to GUI changes? 
	// declare the variables we are going to use in the plugin
	// note to self - static variables are things that should never change and always be the same no matter what instance of the object is alive.
	// class member variables that need to change during execution should not be static!
	// Static can be used to remember last used values,
	// so next time the plugin is run, it remembers the value it used last time. 
	ImagePlus imp;
	int x = 2;
	int y = 15;
	int size = 12;  // default font size
//	int maxWidth; // maxWidth is now a method returning an int
	Font font;
	double start = 4.877;
	double interval = 1.679;
	double lastTime;
	String timeString;
	String customLabelFormat;
	String customSuffix = "";
	static String chosenSuffix = "s";
	String suffix = chosenSuffix;
	int decimalPlaces = 3;
	boolean canceled;
	boolean preview = true;
	String customFormat = "decimal";
	//SimpleDateFormat timeFormat;
	String lastTimeStampString; // = "teststring";
	Checkbox previewCheckbox;

	boolean AAtext = true;
	int frame, first, last;  //these default to 0 as no values are given
	//int nPasses = 1;
	PlugInFilterRunner pfr; 	// set pfr to the default PlugInFilterRunner object - the object that runs the plugin. 
	
	int currentSlice;
	// a combination (bitwise OR) of the flags specified in
	// interfaces PlugInFilter and ExtendedPlugInFilter.
	// determines what kind of image the plug-in can run on etc.
	int flags = DOES_ALL+DOES_STACKS+STACK_REQUIRED;
							
	// a list containing all supported formats, set up at runtime
	ArrayList<LabelFormat> formats = new ArrayList<LabelFormat>();
	// the currently selected format
	LabelFormat selectedFormat;
	
	// a reference to the units drop-down list
	private Choice unitsChoice;
	// a reference to the custom suffix text field
	private TextField customUnitStringField;
	// a reference to the custom format text field
	private TextField customFormatStringField;

	/**
	 * Setup the plug-in and tell ImageJ it needs to work on a stack by returning the flags
	 */
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Time_Stamper_Enhanced.class);
		if (imp!=null) {
			first = 1;
			last = imp.getStackSize();	
		}
		
		// add all supported formats
		formats.add(new DecimalLabelFormat(new String[]{ "y", "w", "d", "h", "min", "s", "ms", "us", "ns", "ps", "fs", "as"}, "Decimal"));
		formats.add(new DigitalLabelFormat(new String[]{"y", "d", "h", "min", "s", "ms"}, "Digital"));
		formats.add(new CustomLabelFormat(new String[]{"Custom Suffix"}, "Custom Format"));
		selectedFormat = formats.get(0);
		
		// return supported flags
		return flags;
	}
	
	/**
	 * Creates a string array out of the names of the available formats.
	 */
	private String[] getAvailableFormats(){
		String[] formatArray = new String[formats.size()];
		int i=0;
		for (LabelFormat t : formats){
			formatArray[i] = t.getName();
			i++;
		}
		return formatArray;
	}

	/**
	 * Make the GUI for the plug-in, with fields to fill all the variables we need.
	 * we are using ExtendedPluginFilter, so first argument is imp not ip.
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {

		// This makes the GUI object 
		GenericDialog gd = new GenericDialog("Time Stamper Enhanced");
		
			// these are the fields of the GUI
		currentSlice = last;
		gd.addSlider("Slice (disabled, dont use me)", 1, imp.getStackSize(), currentSlice);
		( (Scrollbar)(gd.getSliders().elementAt(0) ) ).setEnabled(false);
		
		// this is a choice between digital or decimal
		// but what about mm:ss???
		// options are in the string array timeFormats, default is Decimal:  something.somethingelse 
		String[] fl = getAvailableFormats();
		gd.addChoice("Label format:", fl, fl[0]);
		
		gd.addStringField("Custom Time Format:", customLabelFormat);
		
		// the custom formats text-box could potentially be disabled,
		// depending on the format selection. So save a reference to it.
		customFormatStringField = (TextField) gd.getStringFields().get(0);

		// the list of supported units is determined by the currently selected format
		String[] un = selectedFormat.getAllowedFormatUnits();
		gd.addChoice("Time units:", un, un[0]);
		
		// since we want to modify the contents of the formats drop-down list
		// later, we save s reference to it
		unitsChoice = (Choice) gd.getChoices().get(1);
		
		// we can set a custom suffix and use that by selecting custom
		// suffix in the time units drop down list above
		gd.addStringField("Custom Suffix:", customSuffix);
		
		// the custom suffix text-box could potentially be disabled,
		// depending on the format selection. So save a reference to it.
		customUnitStringField = (TextField) gd.getStringFields().get(1);
		
		
		gd.addNumericField("Starting Time (in s if digital):", start, 2);
		gd.addNumericField("Time Interval Between Frames (in s if digital):", interval, 3);
		gd.addNumericField("X Location:", x, 0);
		gd.addNumericField("Y Location:", y, 0);
		gd.addNumericField("Font Size:", size, 0);
		gd.addNumericField("Decimal Places:", decimalPlaces, 0);
		gd.addNumericField("First Frame:", first, 0);
		gd.addNumericField("Last Frame:", last, 0);

		gd.addCheckbox("Anti-Aliased text for font size 12 or smaller?", true);  //AA only works for font size 12 or smaller!
		
		gd.addPreviewCheckbox(pfr); 	//adds preview checkbox - needs ExtendedPluginFilter and DialogListener!
		previewCheckbox = gd.getPreviewCheckbox();
		gd.addMessage("Time Stamper plugin for Fiji (is just ImageJ - batteries included)\nmaintained by Dan White MPI-CBG dan(at)chalkie.org.uk");
		
		gd.addDialogListener(this); 	//needed for listening to dialog field/button/checkbok changes?
		
		updateUI();
		updateImg();
		
		gd.showDialog();  // shows the dialog GUI!
		
		// handle the plug-in cancel button being pressed.
		if (gd.wasCanceled())
			return DONE;

		// if the ok button was pressed, we are really running the plug-in,
		// so later we can tell what time stamp to make as its not the last
		// as used by preview
		preview = !gd.wasOKed();
		
		// initialise time with the value of the starting time
		///time = start; moved to setNPasses
		
		//imp.startTiming(); //What is this for? Why need to know when it was started... is this used elsewhere..?  
		
		// extendedpluginfilter showDialog method should
		// return a combination (bitwise OR) of the flags specified in
		// interfaces PlugInFilter and ExtendedPlugInFilter.
		return DOES_ALL+DOES_STACKS+STACK_REQUIRED;
	}	
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		// This reads user input parameters from the GUI and listens to changes in GUI fields
		int slice = (int)gd.getNextNumber(); // we dont use this as we read the value of the slider in the getCurrentSliceFromSlider method, but we need to read it so the next hetNextNumber is right. 
		
		// has the label format been changed?
		int currentFormat = gd.getNextChoiceIndex();
		LabelFormat lf = formats.get(currentFormat);
		if (lf != selectedFormat){
			selectedFormat = lf;
			// if the format has changed, we need to modify the
			// units choice accordingly
			unitsChoice.removeAll();
			for (String unit : selectedFormat.getAllowedFormatUnits()) {
				unitsChoice.addItem(unit);
			}
		}
		
		
		customFormat = gd.getNextString();
		
		// get the selected suffix of drop-down list
		chosenSuffix= gd.getNextChoice();
		
		customSuffix = gd.getNextString();
		start = gd.getNextNumber();
 		interval = gd.getNextNumber();
 		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		size = (int)gd.getNextNumber();
		decimalPlaces = (int)gd.getNextNumber();
		first = (int)gd.getNextNumber();
		last = (int)gd.getNextNumber();
		AAtext = gd.getNextBoolean();
		
		// has the slice been changed?
		if (slice != currentSlice) {
			boolean reactivatePreview = false;
			if ( (previewCheckbox != null) && (previewCheckbox.getState() == true) ){
				previewCheckbox.setState(false);
				reactivatePreview = true;
			}
			currentSlice = slice;
			updateImg();
			if (reactivatePreview){
				previewCheckbox.setState(true);
			}
		}
		
		updateUI();
		
		return true;  // or else the dialog will have the ok button deactivated!
	}
	
	/**
	 * Updates some GUI components, based on the current state of the
	 * selected label format.
	 */
	private void updateUI() {
		// if the new format supports custom suffixes, enable
		// the custom suffix text box
		customUnitStringField.setEnabled(selectedFormat.supportsCustomSuffix());
		// if the current format supports custom format, enable
		// the custom format text box
		customFormatStringField.setEnabled(selectedFormat.supportsCustomFormat());
	}
	
	
	/**
	 * This is part of the preview functionality.
	 * Informs the filter of the number of calls of run(ip) that will follow. 
	 * The argument nPasses is worked out by the plug-in runner.
	 */
	public void setNPasses(int nPasses) {
		//frame = first;   // dont need this	
		//time = lastTime();  // set the time to lastTime, when doing the preview run,
				// so the preview does not increment time when clicking the preview box causes run method execution.
				// and i see the longest time stamp that will be made when i do a preview, so i can make sure its where
				// i wanted it.
				//that works, but now the time stamper counts up from time = lastTime value not from  time = (start + (first*interval))
				// when making the time stamps for the whole stack...
		
		if (preview){
			frame = currentSlice;
		}
		else {
			frame = 1; // so the value of frame is reset to 0 each time the plugin is run or the preview checkbox is checked.
		}
		frame--;
	}

	/**
	 * Run the plug-in on the ip object, which is the ImageProcessor object associated with the open/selected image. 
	 * but remember that showDialog method is run before this in ExtendedPluginFilter
	 */
	public void run(ImageProcessor ip) {
		// this increments frame integer by 1. If an int is declared with no value, it defaults to 0
		frame++;
		
		// Updates this image from the pixel data in its associated
		// ImageProcessor object and then displays it
		// if it is the last frame. Why do we need this when there is
		// ip.drawString(timeString); below?
		if (frame==last)
			imp.updateAndDraw();
		
			// the following line isnt needed in ExtendedPluginFilter because setNPasses takes care of number of frames to write in
			// and ExtendedPluginFilter executes the showDialog method before the run method, always, so don't need to call it in run. 
		//if (frame==1) showDialog(imp, "TimeStamperEnhanced", pfr);	// if at the 1st frame of the stack, show the GUI by calling the showDialog method
							// and set the variables according to the GUI input. 
		
		// tell the run method when to not do anything just return  
		// Here there is a bug: with the new use of ExtendedPluginFilter,
		// using preview on, the first time stamp is placed in frame first-1 not first...
		// and the last time stamp in last-1. With preview off it works as expected. 
		if ((!preview) && (canceled || frame<first || frame>last))
			return;
	
		// Have moved the font size and xy loclation calculations for timestamp stuff out of the run method, into their own methods.
		// set the font size according to ROI size, or if no ROI the GUI text input
		setFontParams(ip);
		setLocation(ip);
		double time = getTimeFromFrame(frame);  // ask the getTimeFromFrame method to return the time for that frame
		ip.drawString(selectedFormat.getTimeString(time)); // draw the timestring into the image
		//showProgress(precent done calc here); // dont really need a progress bar... but seem to get one anyway...
	}

	/*
	private void setLabelFormat(){
		String format = "";
		if (timeFormat.equals("hh:mm:ss.ms"))
			format = "HH:mm:ss:SSS";
		else if (timeFormat.equals("Decimal"))
			format =  "";
		else return ("timeFormat was not selected!");
	}
	*/
	
	void setFontParams(ImageProcessor ip) { //work out the size of the font to use from the size of the ROI box drawn, 
											//if one was drawn (how does it know?)
		Rectangle theROI = ip.getRoi();
		// whats up with these numbers? Are they special?
			// single characters fit the ROI, but if the time stamper string is long
			// then the font is too big to fit the whole thing in!
		
		// need to see if we should use the ROI height to set the font size or read it from the plugin gui 
		// if (theROI != null)  doesnt work as if there is no ROI set, the ROI is the size of the image! There is always an ROI!
		// So we can say, if there is no ROI set , its the same size as the image, and if that is the case,
		// we should then use the size as read from the GUI.
		
		if ( theROI.height != ip.getHeight() && theROI.width != ip.getWidth() ) // if the ROI is the same size as the image leave size as it was set by the gui
			size = (int) (theROI.height); // - 1.10526)/0.934211);	     // if there is an ROI not the same size as the image then set size to its height.       
		
		
		
		// make sure the font is not too big or small.... but why? Too -  small cant read it. Too Big - ?
		// should this use private and public and get / set methods?
		// in any case it doesnt seem to work... i can set the font < 7  and it is printed that small. 
		if (size<7) size = 7;
		if (size>80) size = 80;
		// if no ROI, x and y are defaulted or set according to text in gui
		
		// set the font
		font = new Font("SansSerif", Font.PLAIN, size);
		ip.setFont(font);
		
		//more font related setting stuff moved from the run method
		// seems to work more reliable with this code in this method instead of in run.
		// But if i dont change the font size by typing in the GUI - it ignores the AA text setting!!! Why??? 
		ip.setColor(Toolbar.getForegroundColor());
		ip.setAntialiasedText(AAtext);
	}
	
	
	// method to position the time stamp string correctly, so it is all on the image, even for the last frames with bigger numbers. 
	// ip.moveTo(x, y);  // move to x y position for Timestamp writing 
		
	// the maxwidth if statement tries to move the time stamp right a bit to account for the max length the time stamp will be.
	// it's nice to not have the time stamp run off the right edge of the image. 
	// how about subtracting the 
	// maxWidth from the width of the image (x dimension) only if its so close that it will run off.
	// this seems to work now with digital and decimal time formats. 
	void setLocation(ImageProcessor ip) {
	
		// Here we  set x and y at the ROI if there is one (how does it know?), so time stamp is drawn there, not at default x and y. 
		Rectangle roi = ip.getRoi();
		// set the xy time stamp drawing position for ROI smaller than the image, to bottom left of ROI
		if (roi.width<ip.getWidth() || roi.height<ip.getHeight()) {
			x = roi.x;  			// left of the ROI
			y = roi.y+roi.height;  		// bottom of the ROI
		}	
		// make sure the y position is not less than the font height: size, 
		// so the time stamp is not off the top of the image?
		if (y<size)
			y = size;
		// if longest timestamp is wider than (image width - ROI width) , move x in appropriately
		if (maxWidth(ip, selectedFormat.lastTimeStampString()) > ( ip.getWidth() - x ) )
			ip.moveTo( (ip.getWidth() - maxWidth(ip, selectedFormat.lastTimeStampString())), y);
		else ip.moveTo(x, y);
	}
	
	/**
	 * this method  adds a preceeding 0 to a number if it only has one digit instead of two. 
	 * Which is handy for making 00:00 type format strings later. Thx Dscho.
	 */
	String twoDigits(int value) {
		return (value < 10 ? "0" : "") + value;
	}

	/**
	 * Returns the time of the last frame.
	 */
	double lastTime() {
		return getTimeFromFrame(last); 	// is the last time for which a time stamp will be made
	}
	
	/**
	 * Returns the time of a specific frame.
	 * @param f The frame to calculate the time of
	 * @return The time at frame f
	 */
	double getTimeFromFrame(int f) {
		return start + (interval*(f-1)); 	// is the time for a certain frame
	}
	
	//moved out of run method to its own method.
		// maxWidth is an integer = length of the decimal time stamp string in pixels
		// for the last slice of the stack to be stamped. It is used in the run method, 
		// to prevent the time stamp running off the right edge of the image
		// ip.getStringWidth(string) seems to return the # of pixels long a string is in x?
		// how does it take care of font size i wonder? The font is set 
		// using the variable size... so i guess the ip object knows how big the font is.  	
	int maxWidth(ImageProcessor ip, String lastTimeStampString) {
		return ip.getStringWidth(lastTimeStampString);
	}	
		
	
	// to make progress bar in main imagej panel. ... looks like we get one anyway...can see with several hundred frame stack?
	//void showProgress(double percent) {   // dont really need a progress bar...
	//	percent = (double)(frame-1)/nPasses + percent/nPasses;  //whats this for? 
	//	IJ.showProgress(percent);
	//}
	
	private void updateImg(){
		imp.setSlice(currentSlice);
	}
	
	/**
	 * A class representing a supported label format of the time stamper plug-in.
	 * It relates supported format units/suffixes to a format name. Besides that
	 * it determines if a custom suffix should be usable.
	 */
	private abstract class LabelFormat{
		// an array of all the supported units for this format
		String[] allowedFormatUnits;
		// the display name for this format
		String name;
		// a member indicating if a custom suffix should be supported
		boolean customSuffixSupported;
		// a member indicating if a custom format should be supported
		boolean customFormatSupported;
		
		public LabelFormat(String[] allowedFormatUnits, String name, boolean supportCustomSuffix, boolean supportCustomFormat){
			this.allowedFormatUnits = allowedFormatUnits;
			this.name = name;
			this.customSuffixSupported = supportCustomSuffix;
			this.customFormatSupported = supportCustomFormat;
		}
		
		/**
		 * Here we make the strings to print into the images. 
		 * decide if the time format is digital or decimal according to the plugin GUI input
		 * if it is decimal (not digital) then need to set suffix from drop down list
		 * which might be custom suffix if one is entered and selected.
		 * if it is digital, then there is no suffix as format is set hh:mm:ss.ms
		 * @param time
		 * @return
		 */
		public abstract String getTimeString(double time);
		
		/**
		 * this method returns the string of the TimeStamp  for the last frame to be stamped
		 * which is for the frame with value of the last variable. 
		 * It should be the longest string the timestamp will be.
		 * we should use this in maxWidth method and for the preview of the timestamp
		 * used to be: maxWidth = ip.getStringWidth(decimalString(start + interval*imp.getStackSize())); 
		 * but should use last not stacksize, since no time stamp is made for slices after last?
		 * It also needs to calculate maxWidth for both digital and decimal time formats:
		 * @return
		 */
		public String lastTimeStampString() {
			return getTimeString(lastTime());
		}
		
		public String suffix() {
				return chosenSuffix;
		}

		public String[] getAllowedFormatUnits() {
			return allowedFormatUnits;
		}

		public String getName() {
			return name;
		}
		
		public boolean supportsCustomSuffix() {
			return customSuffixSupported;
		}
		
		public boolean supportsCustomFormat() {
			return customFormatSupported;
		}
	}
	
	private class DecimalLabelFormat extends LabelFormat {
		public DecimalLabelFormat(String[] allowedFormatUnits, String name){
			this(allowedFormatUnits, name, false, false);
		}
		
		protected DecimalLabelFormat(String[] allowedFormatUnits, String name, boolean supportCustomSuffix, boolean supportCustomFormat){
			super(allowedFormatUnits, name, supportCustomSuffix, supportCustomFormat);
		}

		/**
		 * Makes the string containing the number for the time stamp, 
		 * with specified decimal places 
		 * format is decimal number with specificed no of digits after the point
		 * if specificed no. of decimal places is 0 then just return the
		 * specified suffix
		 */
		@Override
		public String getTimeString(double time) {
			if (interval==0.0) 
				return suffix(); 
			else
				return (decimalPlaces == 0 ? ""+(int)time : IJ.d2s(time, decimalPlaces)) + " " + suffix(); 
		}
	}
	
	/**
	 * Represents a digital label format, e. g. HH:mm:ss:ms.
	 */
	private class DigitalLabelFormat extends LabelFormat {
		// A calender to calculate time representation with.
		Calendar calendar = new GregorianCalendar();
		
		public DigitalLabelFormat(String[] allowedFormatUnits, String name){
			super(allowedFormatUnits, name, false, true);
		}

		/**
		 * Makes the string containing the number for the time stamp,
		 * with hh:mm:ss.decimalPlaces format
		 * which is nice, but also really need hh:mm:ss and mm:ss.ms etc. 
	 	 * could use the java time/date formating stuff for that?
		 */
		@Override
		public String getTimeString(double time) {
			calendar.setTimeInMillis(0);
			if (chosenSuffix.equals("y")){    //"y", "d", "h", "min", "s", "ms", "us", "ns", "ps", "fs", "as", "Custom Suffix"
				time = time * (365.25*24.0*60.0*60.0*1000.0); //   c.set(Calendar.YEAR, time); // time would have to be an integer... so can't use that
			}
			else if (chosenSuffix.equals("w")){    
				time = time * (7.0*24.0*60.0*60.0*1000.0); 
			}
			else if (chosenSuffix.equals("d")){    
				time = time * (24.0*60.0*60.0*1000.0);
			}
			else if (chosenSuffix.equals("h")){   
				time = time * (60.0*60.0*1000.0);
			}
			else if (chosenSuffix.equals("min")){   
				time = time * (60.0*1000.0);
			}
			else if (chosenSuffix.equals("s")){    
				time = time * 1000.0;
			}
			else if (chosenSuffix.equals("ms")){
				// trivial case, nothing to do:
				// time = time;
			}
			else {
				IJ.error("For a digital 00:00:00 time you must use y, mon, h, min, s or ms only as the time units.");
			}
			
			// set the time
			calendar.setTimeInMillis( Math.round(time) );
			
			SimpleDateFormat f;
			// check if a custom format has been entered
			if (customFormat.length() > 0) {
				// Make sure that our custom format can be handled
				try {
					f = new SimpleDateFormat(customFormat);
				}
				catch (IllegalArgumentException ex) {
					return "Invalid pattern";
				}
			} else {
				f = new SimpleDateFormat("HH:mm:ss.SSS");
			}
			
			return f.format(calendar.getTime() );
			
			/*int hour = (int)(time / 3600);
			time -= hour * 3600;
			int minute = (int)(time / 60);
			time -= minute * 60;
			return twoDigits(hour) + ":" + twoDigits(minute) + ":"
				+ (time < 10 ? "0" : "") 
				+ IJ.d2s(time, decimalPlaces);
			*/
		}
	}
	
	/**
	 * Represents a label format that is essentially the same as a
	 * decimal format, but allows custom suffixes.
	 */
	private class CustomLabelFormat extends DecimalLabelFormat {

		protected CustomLabelFormat(String[] allowedFormatUnits, String name){
			super(allowedFormatUnits, name, true, false);
		}
		
		public String suffix() {
				return customSuffix;
		}
	}
}	// thats the end of Time_Stamper_Enhanced class


 