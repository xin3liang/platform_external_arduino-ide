/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-10 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;
import processing.app.helpers.PreferencesMap;
import processing.app.helpers.filefilters.OnlyDirs;
import processing.app.helpers.filefilters.OnlyFilesWithExtension;
import processing.core.*;
import static processing.app.I18n._;


/**
 * The base class for the main processing application.
 * Primary role of this class is for platform identification and
 * general interaction with the system (launching URLs, loading
 * files and images, etc) that comes from that.
 */
public class Base {
  public static final int REVISION = 101;
  /** This might be replaced by main() if there's a lib/version.txt file. */
  static String VERSION_NAME = "0101";
  /** Set true if this a proper release rather than a numbered revision. */
  static public boolean RELEASE = false;

  static Map<Integer, String> platformNames = new HashMap<Integer, String>();
  static {
    platformNames.put(PConstants.WINDOWS, "windows");
    platformNames.put(PConstants.MACOSX, "macosx");
    platformNames.put(PConstants.LINUX, "linux");
  }

  static HashMap<String, Integer> platformIndices = new HashMap<String, Integer>();
  static {
    platformIndices.put("windows", PConstants.WINDOWS);
    platformIndices.put("macosx", PConstants.MACOSX);
    platformIndices.put("linux", PConstants.LINUX);
  }
  static Platform platform;

  static private boolean commandLine;

  // A single instance of the preferences window
  Preferences preferencesFrame;

  // set to true after the first time the menu is built.
  // so that the errors while building don't show up again.
  boolean builtOnce;

  static File buildFolder;

  // these are static because they're used by Sketch
  static private File examplesFolder;
  static private File toolsFolder;

  static private List<File> librariesFolders;
  
  // maps library name to their library folder
  static private Map<String, File> libraries;
  
  // maps #included files to their library folder
  static Map<String, File> importToLibraryTable;

  // classpath for all known libraries for p5
  // (both those in the p5/libs folder and those with lib subfolders
  // found in the sketchbook)
  static public String librariesClassPath;
  
  static public Map<String, TargetPackage> packages;

  // Location for untitled items
  static File untitledFolder;

  // p5 icon for the window
//  static Image icon;

//  int editorCount;
  List<Editor> editors = Collections.synchronizedList(new ArrayList<Editor>());
  Editor activeEditor;


  static public void main(String args[]) {
    try {
      File versionFile = getContentFile("lib/version.txt");
      if (versionFile.exists()) {
        String version = PApplet.loadStrings(versionFile)[0];
        if (!version.equals(VERSION_NAME)) {
          VERSION_NAME = version;
          RELEASE = true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

//    if (System.getProperty("mrj.version") != null) {
//      //String jv = System.getProperty("java.version");
//      String ov = System.getProperty("os.version");
//      if (ov.startsWith("10.5")) {
//        System.setProperty("apple.laf.useScreenMenuBar", "true");
//      }
//    }

    /*
    commandLine = false;
    if (args.length >= 2) {
      if (args[0].startsWith("--")) {
        commandLine = true;
      }
    }

    if (PApplet.javaVersion < 1.5f) {
      //System.err.println("no way man");
      Base.showError("Need to install Java 1.5",
                     "This version of Processing requires    \n" +
                     "Java 1.5 or later to run properly.\n" +
                     "Please visit java.com to upgrade.", null);
    }
    */

    initPlatform();

//    // Set the look and feel before opening the window
//    try {
//      platform.setLookAndFeel();
//    } catch (Exception e) {
//      System.err.println("Non-fatal error while setting the Look & Feel.");
//      System.err.println("The error message follows, however Processing should run fine.");
//      System.err.println(e.getMessage());
//      //e.printStackTrace();
//    }

    // Use native popups so they don't look so crappy on osx
    JPopupMenu.setDefaultLightWeightPopupEnabled(false);

    // Don't put anything above this line that might make GUI,
    // because the platform has to be inited properly first.

    // Make sure a full JDK is installed
    //initRequirements();

    // run static initialization that grabs all the prefs
    Preferences.init(null);

    // load the I18n module for internationalization
    I18n.init(Preferences.get("editor.languages.current"));

    // setup the theme coloring fun
    Theme.init();

    // Set the look and feel before opening the window
    try {
      platform.setLookAndFeel();
    } catch (Exception e) {
      String mess = e.getMessage();
      if (mess.indexOf("ch.randelshofer.quaqua.QuaquaLookAndFeel") == -1) {
        System.err.println(_("Non-fatal error while setting the Look & Feel."));
        System.err.println(_("The error message follows, however Arduino should run fine."));
        System.err.println(mess);
      }
    }

    // Create a location for untitled sketches
    untitledFolder = createTempFolder("untitled");
    untitledFolder.deleteOnExit();

    new Base(args);
  }


  static protected void setCommandLine() {
    commandLine = true;
  }


  static protected boolean isCommandLine() {
    return commandLine;
  }


  static protected void initPlatform() {
    try {
      Class<?> platformClass = Class.forName("processing.app.Platform");
      if (Base.isMacOS()) {
        platformClass = Class.forName("processing.app.macosx.Platform");
      } else if (Base.isWindows()) {
        platformClass = Class.forName("processing.app.windows.Platform");
      } else if (Base.isLinux()) {
        platformClass = Class.forName("processing.app.linux.Platform");
      }
      platform = (Platform) platformClass.newInstance();
    } catch (Exception e) {
      Base.showError(_("Problem Setting the Platform"),
                     _("An unknown error occurred while trying to load\n" +
                       "platform-specific code for your machine."), e);
    }
  }


  static protected void initRequirements() {
    try {
      Class.forName("com.sun.jdi.VirtualMachine");
    } catch (ClassNotFoundException cnfe) {
      Base.showPlatforms();
      Base.showError(_("Please install JDK 1.5 or later"),
                     _("Arduino requires a full JDK (not just a JRE)\n" +
                       "to run. Please install JDK 1.5 or later.\n" +
                       "More information can be found in the reference."), cnfe);
    }
  }


  public Base(String[] args) {
    platform.init(this);

    // Get the sketchbook path, and make sure it's set properly
    String sketchbookPath = Preferences.get("sketchbook.path");

    // If a value is at least set, first check to see if the folder exists.
    // If it doesn't, warn the user that the sketchbook folder is being reset.
    if (sketchbookPath != null) {
      File skechbookFolder = new File(sketchbookPath);
      if (!skechbookFolder.exists()) {
        Base.showWarning(_("Sketchbook folder disappeared"),
                         _("The sketchbook folder no longer exists.\n" +
                           "Arduino will switch to the default sketchbook\n" +
                           "location, and create a new sketchbook folder if\n" +
                           "necessary. Arduino will then stop talking about\n" +
                           "himself in the third person."), null);
        sketchbookPath = null;
      }
    }

    // If no path is set, get the default sketchbook folder for this platform
    if (sketchbookPath == null) {
      File defaultFolder = getDefaultSketchbookFolder();
      Preferences.set("sketchbook.path", defaultFolder.getAbsolutePath());
      if (!defaultFolder.exists()) {
        defaultFolder.mkdirs();
      }
    }
    
    packages = new HashMap<String, TargetPackage>();
    loadHardware(getHardwareFolder());
    loadHardware(getSketchbookHardwareFolder());
    // Setup board-dependent variables.
    onBoardOrPortChange();

    // Check if there were previously opened sketches to be restored
    boolean opened = restoreSketches();

    // Check if any files were passed in on the command line
    for (int i = 0; i < args.length; i++) {
      String path = args[i];
      // Fix a problem with systems that use a non-ASCII languages. Paths are
      // being passed in with 8.3 syntax, which makes the sketch loader code
      // unhappy, since the sketch folder naming doesn't match up correctly.
      // http://dev.processing.org/bugs/show_bug.cgi?id=1089
      if (isWindows()) {
        try {
          File file = new File(args[i]);
          path = file.getCanonicalPath();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (handleOpen(path) != null) {
        opened = true;
      }
    }

    // Create a new empty window (will be replaced with any files to be opened)
    if (!opened) {
      handleNew();
    }

    // Check for updates
    if (Preferences.getBoolean("update.check")) {
      new UpdateCheck(this);
    }
  }


  /**
   * Post-constructor setup for the editor area. Loads the last
   * sketch that was used (if any), and restores other Editor settings.
   * The complement to "storePreferences", this is called when the
   * application is first launched.
   */
  protected boolean restoreSketches() {
    // figure out window placement

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    boolean windowPositionValid = true;

    if (Preferences.get("last.screen.height") != null) {
      // if screen size has changed, the window coordinates no longer
      // make sense, so don't use them unless they're identical
      int screenW = Preferences.getInteger("last.screen.width");
      int screenH = Preferences.getInteger("last.screen.height");

      if ((screen.width != screenW) || (screen.height != screenH)) {
        windowPositionValid = false;
      }
      /*
      int windowX = Preferences.getInteger("last.window.x");
      int windowY = Preferences.getInteger("last.window.y");
      if ((windowX < 0) || (windowY < 0) ||
          (windowX > screenW) || (windowY > screenH)) {
        windowPositionValid = false;
      }
      */
    } else {
      windowPositionValid = false;
    }

    // Iterate through all sketches that were open last time p5 was running.
    // If !windowPositionValid, then ignore the coordinates found for each.

    // Save the sketch path and window placement for each open sketch
    int count = Preferences.getInteger("last.sketch.count");
    int opened = 0;
    for (int i = 0; i < count; i++) {
      String path = Preferences.get("last.sketch" + i + ".path");
      int[] location;
      if (windowPositionValid) {
        String locationStr = Preferences.get("last.sketch" + i + ".location");
        location = PApplet.parseInt(PApplet.split(locationStr, ','));
      } else {
        location = nextEditorLocation();
      }
      // If file did not exist, null will be returned for the Editor
      if (handleOpen(path, location) != null) {
        opened++;
      }
    }
    return (opened > 0);
  }


  /**
   * Store list of sketches that are currently open.
   * Called when the application is quitting and documents are still open.
   */
  protected void storeSketches() {
    // Save the width and height of the screen
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    Preferences.setInteger("last.screen.width", screen.width);
    Preferences.setInteger("last.screen.height", screen.height);

    String untitledPath = untitledFolder.getAbsolutePath();

    // Save the sketch path and window placement for each open sketch
    int index = 0;
    for (Editor editor : editors) {
      String path = editor.getSketch().getMainFilePath();
      // In case of a crash, save untitled sketches if they contain changes.
      // (Added this for release 0158, may not be a good idea.)
      if (path.startsWith(untitledPath) &&
          !editor.getSketch().isModified()) {
        continue;
      }
      Preferences.set("last.sketch" + index + ".path", path);

      int[] location = editor.getPlacement();
      String locationStr = PApplet.join(PApplet.str(location), ",");
      Preferences.set("last.sketch" + index + ".location", locationStr);
      index++;
    }
    Preferences.setInteger("last.sketch.count", index);
  }


  // If a sketch is untitled on quit, may need to store the new name
  // rather than the location from the temp folder.
  protected void storeSketchPath(Editor editor, int index) {
    String path = editor.getSketch().getMainFilePath();
    String untitledPath = untitledFolder.getAbsolutePath();
    if (path.startsWith(untitledPath)) {
      path = "";
    }
    Preferences.set("last.sketch" + index + ".path", path);
  }


  /*
  public void storeSketch(Editor editor) {
    int index = -1;
    for (int i = 0; i < editorCount; i++) {
      if (editors[i] == editor) {
        index = i;
        break;
      }
    }
    if (index == -1) {
      System.err.println("Problem storing sketch " + editor.sketch.name);
    } else {
      String path = editor.sketch.getMainFilePath();
      Preferences.set("last.sketch" + index + ".path", path);
    }
  }
  */


  // .................................................................


  // Because of variations in native windowing systems, no guarantees about
  // changes to the focused and active Windows can be made. Developers must
  // never assume that this Window is the focused or active Window until this
  // Window receives a WINDOW_GAINED_FOCUS or WINDOW_ACTIVATED event.
  protected void handleActivated(Editor whichEditor) {
    activeEditor = whichEditor;

    // set the current window to be the console that's getting output
    EditorConsole.setEditor(activeEditor);
  }


  protected int[] nextEditorLocation() {
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int defaultWidth = Preferences.getInteger("editor.window.width.default");
    int defaultHeight = Preferences.getInteger("editor.window.height.default");

    if (activeEditor == null) {
      // If no current active editor, use default placement
      return new int[] {
          (screen.width - defaultWidth) / 2,
          (screen.height - defaultHeight) / 2,
          defaultWidth, defaultHeight, 0
      };

    } else {
      // With a currently active editor, open the new window
      // using the same dimensions, but offset slightly.
      synchronized (editors) {
        final int OVER = 50;
        // In release 0160, don't
        //location = activeEditor.getPlacement();
        Editor lastOpened = editors.get(editors.size() - 1);
        int[] location = lastOpened.getPlacement();
        // Just in case the bounds for that window are bad
        location[0] += OVER;
        location[1] += OVER;

        if (location[0] == OVER ||
            location[2] == OVER ||
            location[0] + location[2] > screen.width ||
            location[1] + location[3] > screen.height) {
          // Warp the next window to a randomish location on screen.
          return new int[] {
              (int) (Math.random() * (screen.width - defaultWidth)),
              (int) (Math.random() * (screen.height - defaultHeight)),
              defaultWidth, defaultHeight, 0
          };
        }

        return location;
      }
    }
  }


  // .................................................................


  boolean breakTime = false;
  String[] months = {
    "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "dec"
  };

  /**
   * Handle creating a sketch folder, return its base .pde file
   * or null if the operation was canceled.
   * @param shift whether shift is pressed, which will invert prompt setting
   * @param noPrompt disable prompt, no matter the setting
   */
  protected String createNewUntitled() throws IOException {
    File newbieDir = null;
    String newbieName = null;

    // In 0126, untitled sketches will begin in the temp folder,
    // and then moved to a new location because Save will default to Save As.
    File sketchbookDir = getSketchbookFolder();
    File newbieParentDir = untitledFolder;

    // Use a generic name like sketch_031008a, the date plus a char
    int index = 0;
    //SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");
    //SimpleDateFormat formatter = new SimpleDateFormat("MMMdd");
    //String purty = formatter.format(new Date()).toLowerCase();
    Calendar cal = Calendar.getInstance();
    int day = cal.get(Calendar.DAY_OF_MONTH);  // 1..31
    int month = cal.get(Calendar.MONTH);  // 0..11
    String purty = months[month] + PApplet.nf(day, 2);
    do {
      if (index == 26) {
        // In 0159, avoid running past z by sending people outdoors.
        if (!breakTime) {
          Base.showWarning(_("Time for a Break"),
                           _("You've reached the limit for auto naming of new sketches\n" +
                             "for the day. How about going for a walk instead?"), null);
          breakTime = true;
        } else {
          Base.showWarning(_("Sunshine"),
                           _("No really, time for some fresh air for you."), null);
        }
        return null;
      }
      newbieName = "sketch_" + purty + ((char) ('a' + index));
      newbieDir = new File(newbieParentDir, newbieName);
      index++;
      // Make sure it's not in the temp folder *and* it's not in the sketchbook
    } while (newbieDir.exists() || new File(sketchbookDir, newbieName).exists());

    // Make the directory for the new sketch
    newbieDir.mkdirs();

    // Make an empty pde file
    File newbieFile = new File(newbieDir, newbieName + ".ino");
    new FileOutputStream(newbieFile);  // create the file
    return newbieFile.getAbsolutePath();
  }


  /**
   * Create a new untitled document in a new sketch window.
   */
  public void handleNew() {
    try {
      String path = createNewUntitled();
      if (path != null) {
        Editor editor = handleOpen(path);
        editor.untitled = true;
      }

    } catch (IOException e) {
      if (activeEditor != null) {
        activeEditor.statusError(e);
      }
    }
  }


  /**
   * Replace the sketch in the current window with a new untitled document.
   */
  public void handleNewReplace() {
    if (!activeEditor.checkModified()) {
      return;  // sketch was modified, and user canceled
    }
    // Close the running window, avoid window boogers with multiple sketches
    activeEditor.internalCloseRunner();

    // Actually replace things
    handleNewReplaceImpl();
  }


  protected void handleNewReplaceImpl() {
    try {
      String path = createNewUntitled();
      if (path != null) {
        activeEditor.handleOpenInternal(path);
        activeEditor.untitled = true;
      }
//      return true;

    } catch (IOException e) {
      activeEditor.statusError(e);
//      return false;
    }
  }


  /**
   * Open a sketch, replacing the sketch in the current window.
   * @param path Location of the primary pde file for the sketch.
   */
  public void handleOpenReplace(String path) {
    if (!activeEditor.checkModified()) {
      return;  // sketch was modified, and user canceled
    }
    // Close the running window, avoid window boogers with multiple sketches
    activeEditor.internalCloseRunner();

    boolean loaded = activeEditor.handleOpenInternal(path);
    if (!loaded) {
      // replace the document without checking if that's ok
      handleNewReplaceImpl();
    }
  }


  /**
   * Prompt for a sketch to open, and open it in a new window.
   */
  public void handleOpenPrompt() {
    // get the frontmost window frame for placing file dialog
    FileDialog fd = new FileDialog(activeEditor,
                                   _("Open an Arduino sketch..."),
                                   FileDialog.LOAD);
    // This was annoying people, so disabled it in 0125.
    //fd.setDirectory(Preferences.get("sketchbook.path"));
    //fd.setDirectory(getSketchbookPath());

    // Only show .pde files as eligible bachelors
    fd.setFilenameFilter(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.toLowerCase().endsWith(".ino")
              || name.toLowerCase().endsWith(".pde");
        }
      });

    fd.setVisible(true);

    String directory = fd.getDirectory();
    String filename = fd.getFile();

    // User canceled selection
    if (filename == null) return;

    File inputFile = new File(directory, filename);
    handleOpen(inputFile.getAbsolutePath());
  }


  /**
   * Open a sketch in a new window.
   * @param path Path to the pde file for the sketch in question
   * @return the Editor object, so that properties (like 'untitled')
   *         can be set by the caller
   */
  public Editor handleOpen(String path) {
    return handleOpen(path, nextEditorLocation());
  }


  protected Editor handleOpen(String path, int[] location) {
//    System.err.println("entering handleOpen " + path);

    File file = new File(path);
    if (!file.exists()) return null;

//    System.err.println("  editors: " + editors);
    // Cycle through open windows to make sure that it's not already open.
    for (Editor editor : editors) {
      if (editor.getSketch().getMainFilePath().equals(path)) {
        editor.toFront();
//        System.err.println("  handleOpen: already opened");
        return editor;
      }
    }

    // If the active editor window is an untitled, and un-modified document,
    // just replace it with the file that's being opened.
//    if (activeEditor != null) {
//      Sketch activeSketch = activeEditor.sketch;
//      if (activeSketch.isUntitled() && !activeSketch.isModified()) {
//        // if it's an untitled, unmodified document, it can be replaced.
//        // except in cases where a second blank window is being opened.
//        if (!path.startsWith(untitledFolder.getAbsolutePath())) {
//          activeEditor.handleOpenUnchecked(path, 0, 0, 0, 0);
//          return activeEditor;
//        }
//      }
//    }

//    System.err.println("  creating new editor");
    Editor editor = new Editor(this, path, location);
//    Editor editor = null;
//    try {
//      editor = new Editor(this, path, location);
//    } catch (Exception e) {
//      e.printStackTrace();
//      System.err.flush();
//      System.out.flush();
//      System.exit(1);
//    }
//    System.err.println("  done creating new editor");
//    EditorConsole.systemErr.println("  done creating new editor");

    // Make sure that the sketch actually loaded
    if (editor.getSketch() == null) {
//      System.err.println("sketch was null, getting out of handleOpen");
      return null;  // Just walk away quietly
    }

//    if (editors == null) {
//      editors = new Editor[5];
//    }
//    if (editorCount == editors.length) {
//      editors = (Editor[]) PApplet.expand(editors);
//    }
//    editors[editorCount++] = editor;
    editors.add(editor);

//    if (markedForClose != null) {
//      Point p = markedForClose.getLocation();
//      handleClose(markedForClose, false);
//      // open the new window in
//      editor.setLocation(p);
//    }

    // now that we're ready, show the window
    // (don't do earlier, cuz we might move it based on a window being closed)
    editor.setVisible(true);

//    System.err.println("exiting handleOpen");

    return editor;
  }


  /**
   * Close a sketch as specified by its editor window.
   * @param editor Editor object of the sketch to be closed.
   * @return true if succeeded in closing, false if canceled.
   */
  public boolean handleClose(Editor editor) {
    // Check if modified
//    boolean immediate = editors.size() == 1;
    if (!editor.checkModified()) {
      return false;
    }

    // Close the running window, avoid window boogers with multiple sketches
    editor.internalCloseRunner();

    if (editors.size() == 1) {
      // For 0158, when closing the last window /and/ it was already an
      // untitled sketch, just give up and let the user quit.
//      if (Preferences.getBoolean("sketchbook.closing_last_window_quits") ||
//          (editor.untitled && !editor.getSketch().isModified())) {
      if (Base.isMacOS()) {
        Object[] options = { "OK", "Cancel" };
        String prompt =
          _("<html> " +
            "<head> <style type=\"text/css\">"+
            "b { font: 13pt \"Lucida Grande\" }"+
            "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
            "</style> </head>" +
            "<b>Are you sure you want to Quit?</b>" +
            "<p>Closing the last open sketch will quit Arduino.");

        int result = JOptionPane.showOptionDialog(editor,
                                                  prompt,
                                                  _("Quit"),
                                                  JOptionPane.YES_NO_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);
        if (result == JOptionPane.NO_OPTION ||
            result == JOptionPane.CLOSED_OPTION) {
          return false;
        }
      }

      // This will store the sketch count as zero
      editors.remove(editor);
      Editor.serialMonitor.closeSerialPort();
      storeSketches();

      // Save out the current prefs state
      Preferences.save();

      // Since this wasn't an actual Quit event, call System.exit()
      System.exit(0);

    } else {
      // More than one editor window open,
      // proceed with closing the current window.
      editor.setVisible(false);
      editor.dispose();
//      for (int i = 0; i < editorCount; i++) {
//        if (editor == editors[i]) {
//          for (int j = i; j < editorCount-1; j++) {
//            editors[j] = editors[j+1];
//          }
//          editorCount--;
//          // Set to null so that garbage collection occurs
//          editors[editorCount] = null;
//        }
//      }
      editors.remove(editor);
    }
    return true;
  }


  /**
   * Handler for File &rarr; Quit.
   * @return false if canceled, true otherwise.
   */
  public boolean handleQuit() {
    // If quit is canceled, this will be replaced anyway
    // by a later handleQuit() that is not canceled.
    storeSketches();
    Editor.serialMonitor.closeSerialPort();

    if (handleQuitEach()) {
      // make sure running sketches close before quitting
      for (Editor editor : editors) {
        editor.internalCloseRunner();
      }
      // Save out the current prefs state
      Preferences.save();

      if (!Base.isMacOS()) {
        // If this was fired from the menu or an AppleEvent (the Finder),
        // then Mac OS X will send the terminate signal itself.
        System.exit(0);
      }
      return true;
    }
    return false;
  }


  /**
   * Attempt to close each open sketch in preparation for quitting.
   * @return false if canceled along the way
   */
  protected boolean handleQuitEach() {
    int index = 0;
    for (Editor editor : editors) {
      if (editor.checkModified()) {
        // Update to the new/final sketch path for this fella
        storeSketchPath(editor, index);
        index++;

      } else {
        return false;
      }
    }
    return true;
  }


  // .................................................................


  /**
   * Asynchronous version of menu rebuild to be used on save and rename
   * to prevent the interface from locking up until the menus are done.
   */
  protected void rebuildSketchbookMenus() {
    //System.out.println("async enter");
    //new Exception().printStackTrace();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        //System.out.println("starting rebuild");
        rebuildSketchbookMenu(Editor.sketchbookMenu);
        rebuildToolbarMenu(Editor.toolbarMenu);
        //System.out.println("done with rebuild");
      }
    });
    //System.out.println("async exit");
  }


  protected void rebuildToolbarMenu(JMenu menu) {
    JMenuItem item;
    menu.removeAll();

    // Add the single "Open" item
    item = Editor.newJMenuItem(_("Open..."), 'O');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleOpenPrompt();
        }
      });
    menu.add(item);
    menu.addSeparator();

    // Add a list of all sketches and subfolders
    try {
      boolean sketches = addSketches(menu, getSketchbookFolder(), true);
      if (sketches) menu.addSeparator();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Add each of the subfolders of examples directly to the menu
    try {
      boolean found = addSketches(menu, examplesFolder, true);
      if (found) menu.addSeparator();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  protected void rebuildSketchbookMenu(JMenu menu) {
    //System.out.println("rebuilding sketchbook menu");
    //new Exception().printStackTrace();
    try {
        menu.removeAll();
      addSketches(menu, getSketchbookFolder(), false);
      //addSketches(menu, getSketchbookFolder());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void rebuildImportMenu(JMenu importMenu) {
    importMenu.removeAll();

    try {
      // Find the current target. Get the platform, and then select the
      // correct name and core path.
      PreferencesMap prefs = getTargetPlatform().getPreferences();
      String targetname = prefs.get("name");

      JMenuItem platformItem = new JMenuItem(targetname);
      platformItem.setEnabled(false);
      importMenu.add(platformItem);
      importMenu.addSeparator();
      addLibraries(importMenu, libraries);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void rebuildExamplesMenu(JMenu menu) {
    try {
      menu.removeAll();
      
      // Add examples from distribution "example" folder
      boolean found = addSketches(menu, examplesFolder, false);
      if (found) menu.addSeparator();

      // Add examples from libraries
      List<String> names = new ArrayList<String>(libraries.keySet());
      Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
      for (String name : names) {
        File folder = libraries.get(name);
        addSketchesSubmenu(menu, name, folder, false);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public Map<String, File> scanLibraries(List<File> folders) {
    Map<String, File> res = new HashMap<String, File>(); 
    for (File folder : folders)
      res.putAll(scanLibraries(folder));
    return res;
  }
  
  public Map<String, File> scanLibraries(File folder) {
    Map<String, File> res = new HashMap<String, File>();
    String list[] = folder.list(new OnlyDirs());
    // if a bad folder or something like that, this might come back null
    if (list == null)
      return res;

    for (String libName : list) {
      File subfolder = new File(folder, libName);
      if (!Sketch.isSanitaryName(libName)) {
        String mess = I18n.format(_("The library \"{0}\" cannot be used.\n"
            + "Library names must contain only basic letters and numbers.\n"
            + "(ASCII only and no spaces, and it cannot start with a number)"),
                                  libName);
        Base.showMessage(_("Ignoring bad library name"), mess);
        continue;
      }

      // (also replace previously found libs with the same name) 
      res.put(libName, subfolder);
    }
    return res;
  }
  
  public void onBoardOrPortChange() {
    // Calculate paths for libraries and examples
    examplesFolder = getContentFile("examples");
    toolsFolder = getContentFile("tools");
    
    File platformFolder = getTargetPlatform().getFolder();
    librariesFolders = new ArrayList<File>();
    librariesFolders.add(getContentFile("libraries"));
    librariesFolders.add(new File(platformFolder, "libraries"));
    librariesFolders.add(getSketchbookLibrariesFolder());
    
    // Scan for libraries in each library folder.
    // Libraries located in the latest folders on the list can override
    // other libraries with the same.
    libraries = scanLibraries(librariesFolders);

    // Populate importToLibraryTable
    importToLibraryTable = new HashMap<String, File>();
    for (File subfolder : libraries.values()) {
      String packages[] = headerListFromIncludePath(subfolder);
      for (String pkg : packages)
        importToLibraryTable.put(pkg, subfolder);
    }
    
    // Update editors status bar
    for (Editor editor : editors)
      editor.onBoardOrPortChange();
  }

  
  public void rebuildBoardsMenu(JMenu menu) {
    String selPackage = Preferences.get("target_package");
    String selPlatform = Preferences.get("target_platform");
    String selBoard = Preferences.get("board");

    menu.removeAll();
    ButtonGroup group = new ButtonGroup();
    // Cycle through all packages
    for (TargetPackage targetPackage : packages.values()) {
      String packageName = targetPackage.getName();
      
      // For every package cycle through all platform
      for (TargetPlatform targetPlatform : targetPackage.platforms()) {
        String platformName = targetPlatform.getName();
        Map<String, PreferencesMap> boards = targetPlatform.getBoards();
        
        // For every platform cycle throug all boards
        for (String board : boards.keySet()) {
          
          // Setup a menu item for the current board
          String boardName = boards.get(board).get("name");
          @SuppressWarnings("serial")
          AbstractAction action = new AbstractAction(boardName) {
            public void actionPerformed(ActionEvent actionevent) {
              Preferences.set("target_package", (String) getValue("package"));
              Preferences.set("target_platform", (String) getValue("platform"));
              Preferences.set("board", (String) getValue("board"));
              
              onBoardOrPortChange();
              Sketch.buildSettingChanged();
              rebuildImportMenu(Editor.importMenu);
              rebuildExamplesMenu(Editor.examplesMenu);
            }
          };
          action.putValue("package", packageName);
          action.putValue("platform", platformName);
          action.putValue("board", board);
          JMenuItem item = new JRadioButtonMenuItem(action);
          if (packageName.equals(selPackage) &&
              platformName.equals(selPlatform) && board.equals(selBoard)) {
            item.setSelected(true);
          }
          group.add(item);
          menu.add(item);
        }
      }
    }
  }
  
  public void rebuildProgrammerMenu(JMenu menu) {
    menu.removeAll();
    ButtonGroup group = new ButtonGroup();
    for (TargetPackage targetPackage : packages.values()) {
      for (TargetPlatform targetPlatform : targetPackage.platforms()) {
        for (String programmer : targetPlatform.getProgrammers().keySet()) {
          String id = targetPackage.getName() + ":" + programmer;

          @SuppressWarnings("serial")
          AbstractAction action = new AbstractAction(targetPlatform
              .getProgrammer(programmer).get("name")) {
            public void actionPerformed(ActionEvent actionevent) {
              Preferences.set("programmer", "" + getValue("id"));
            }
          };
          action.putValue("id", id);
          JMenuItem item = new JRadioButtonMenuItem(action);
          if (Preferences.get("programmer").equals(id))
            item.setSelected(true);
          group.add(item);
          menu.add(item);
        }
      }
    }
  }

  /**
   * Scan a folder recursively, and add any sketches found to the menu
   * specified. Set the openReplaces parameter to true when opening the sketch
   * should replace the sketch in the current window, or false when the
   * sketch should open in a new window.
   */
  protected boolean addSketches(JMenu menu, File folder,
                                final boolean replaceExisting) throws IOException {
    // skip .DS_Store files, etc (this shouldn't actually be necessary)
    if (!folder.isDirectory()) return false;

    String[] list = folder.list();
    // If a bad folder or unreadable or whatever, this will come back null
    if (list == null) return false;

    // Alphabetize list, since it's not always alpha order
    Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);

    boolean ifound = false;

    for (String name : list) {
      if ((name.charAt(0) == '.') ||
          name.equals("CVS")) continue;

      File subfolder = new File(folder, name);
      if (!subfolder.isDirectory()) continue;

      if (addSketchesSubmenu(menu, name, subfolder, replaceExisting))
        ifound = true;
    }
    
    return ifound;  // actually ignored, but..
  }

  private boolean addSketchesSubmenu(JMenu menu, String name, File folder,
                         final boolean replaceExisting) throws IOException {

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String path = e.getActionCommand();
        if (new File(path).exists()) {
          boolean replace = replaceExisting;
          if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
            replace = !replace;
          }
          if (replace) {
            handleOpenReplace(path);
          } else {
            handleOpen(path);
          }
        } else {
          showWarning(_("Sketch Does Not Exist"),
                      _("The selected sketch no longer exists.\n"
                          + "You may need to restart Arduino to update\n"
                          + "the sketchbook menu."), null);
        }
      }
    };

    File entry = new File(folder, name + ".ino");
    if (!entry.exists() && (new File(folder, name + ".pde")).exists())
      entry = new File(folder, name + ".pde");

    // if a .pde file of the same prefix as the folder exists..
    if (entry.exists()) {

      if (!Sketch.isSanitaryName(name)) {
        if (!builtOnce) {
          String complaining = I18n
              .format(
                      _("The sketch \"{0}\" cannot be used.\n"
                          + "Sketch names must contain only basic letters and numbers\n"
                          + "(ASCII-only with no spaces, "
                          + "and it cannot start with a number).\n"
                          + "To get rid of this message, remove the sketch from\n"
                          + "{1}"), name, entry.getAbsolutePath());
          Base.showMessage(_("Ignoring sketch with bad name"), complaining);
        }
        return false;
      }

      JMenuItem item = new JMenuItem(name);
      item.addActionListener(listener);
      item.setActionCommand(entry.getAbsolutePath());
      menu.add(item);
      return true;
    } 

    // don't create an extra menu level for a folder named "examples"
    if (folder.getName().equals("examples"))
      return addSketches(menu, folder, replaceExisting);

    // not a sketch folder, but maybe a subfolder containing sketches
    JMenu submenu = new JMenu(name);
    boolean found = addSketches(submenu, folder, replaceExisting);
    if (found)
      menu.add(submenu);
    return found;
  }

  protected boolean addLibraries(JMenu menu, Map<String, File> libs) throws IOException {

    List<String> list = new ArrayList<String>(libs.keySet());
    Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
    
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        activeEditor.getSketch().importLibrary(e.getActionCommand());
      }
    };

    boolean found = false;

    for (String name : list) {
      File folder = libs.get(name);

      // Add new element at the bottom
      JMenuItem item = new JMenuItem(name);
      item.addActionListener(listener);
      item.setActionCommand(folder.getAbsolutePath());
      menu.add(item);
      found = true;

      // XXX: DAM: should recurse here so that library folders can be nested
    }
    return found;
  }
 
  /**
   * Given a folder, return a list of the header files in that folder (but not
   * the header files in its sub-folders, as those should be included from
   * within the header files at the top-level).
   */
  static public String[] headerListFromIncludePath(File path) {
    return path.list(new OnlyFilesWithExtension(".h"));
  }
 
  protected void loadHardware(File folder) {
    if (!folder.isDirectory()) return;
    
    String list[] = folder.list(new OnlyDirs());
    
    // if a bad folder or something like that, this might come back null
    if (list == null) return;

    // alphabetize list, since it's not always alpha order
    // replaced hella slow bubble sort with this feller for 0093
    Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
    
    for (String target : list) {
      // Skip reserved 'tools' folder.
      if (target.equals("tools"))
        continue;
      File subfolder = new File(folder, target);
      packages.put(target, new TargetPackage(target, subfolder));
    }
  }


  // .................................................................


  /**
   * Show the About box.
   */
  @SuppressWarnings("serial")
  public void handleAbout() {
    final Image image = Base.getLibImage("about.jpg", activeEditor);
    final Window window = new Window(activeEditor) {
        public void paint(Graphics g) {
          g.drawImage(image, 0, 0, null);

          Graphics2D g2 = (Graphics2D) g;
          g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                              RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

          g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
          g2.setColor(Color.white);
          g2.drawString(Base.VERSION_NAME, 50, 30);

          g2.drawString("This IDE is only for the ADK2012 board,", 30, 220);
          g2.drawString("and is based on the IDE from the Arduino Project.", 30, 240);
          g2.drawString("It was created in cooperation with the Arduino team.", 30, 260);
        }
      };
    window.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          window.dispose();
        }
      });
    int w = image.getWidth(activeEditor);
    int h = image.getHeight(activeEditor);
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    window.setBounds((screen.width-w)/2, (screen.height-h)/2, w, h);
    window.setVisible(true);
  }


  /**
   * Show the Preferences window.
   */
  public void handlePrefs() {
    if (preferencesFrame == null) preferencesFrame = new Preferences();
    preferencesFrame.showFrame(activeEditor);
  }


  // ...................................................................


  /**
   * Get list of platform constants.
   */
//  static public int[] getPlatforms() {
//    return platforms;
//  }


//  static public int getPlatform() {
//    String osname = System.getProperty("os.name");
//
//    if (osname.indexOf("Mac") != -1) {
//      return PConstants.MACOSX;
//
//    } else if (osname.indexOf("Windows") != -1) {
//      return PConstants.WINDOWS;
//
//    } else if (osname.equals("Linux")) {  // true for the ibm vm
//      return PConstants.LINUX;
//
//    } else {
//      return PConstants.OTHER;
//    }
//  }


  static public Platform getPlatform() {
    return platform;
  }


  static public String getPlatformName() {
    String osname = System.getProperty("os.name");

    if (osname.indexOf("Mac") != -1) {
      return "macosx";

    } else if (osname.indexOf("Windows") != -1) {
      return "windows";

    } else if (osname.equals("Linux")) {  // true for the ibm vm
      return "linux";

    } else {
      return "other";
    }
  }


  /**
   * Map a platform constant to its name.
   * @param which PConstants.WINDOWS, PConstants.MACOSX, PConstants.LINUX
   * @return one of "windows", "macosx", or "linux"
   */
  static public String getPlatformName(int which) {
    return platformNames.get(which);
  }


  static public int getPlatformIndex(String what) {
    Integer entry = platformIndices.get(what);
    return (entry == null) ? -1 : entry.intValue();
  }


  // These were changed to no longer rely on PApplet and PConstants because
  // of conflicts that could happen with older versions of core.jar, where
  // the MACOSX constant would instead read as the LINUX constant.


  /**
   * returns true if Processing is running on a Mac OS X machine.
   */
  static public boolean isMacOS() {
    //return PApplet.platform == PConstants.MACOSX;
    return System.getProperty("os.name").indexOf("Mac") != -1;
  }


  /**
   * returns true if running on windows.
   */
  static public boolean isWindows() {
    //return PApplet.platform == PConstants.WINDOWS;
    return System.getProperty("os.name").indexOf("Windows") != -1;
  }


  /**
   * true if running on linux.
   */
  static public boolean isLinux() {
    //return PApplet.platform == PConstants.LINUX;
    return System.getProperty("os.name").indexOf("Linux") != -1;
  }


  // .................................................................


  static public File getSettingsFolder() {
    File settingsFolder = null;

    String preferencesPath = Preferences.get("settings.path");
    if (preferencesPath != null) {
      settingsFolder = new File(preferencesPath);

    } else {
      try {
        settingsFolder = platform.getSettingsFolder();
      } catch (Exception e) {
        showError(_("Problem getting data folder"),
                  _("Error getting the Arduino data folder."), e);
      }
    }

    // create the folder if it doesn't exist already
    if (!settingsFolder.exists()) {
      if (!settingsFolder.mkdirs()) {
        showError(_("Settings issues"),
                  _("Arduino cannot run because it could not\n" +
                    "create a folder to store your settings."), null);
      }
    }
    return settingsFolder;
  }


  /**
   * Convenience method to get a File object for the specified filename inside
   * the settings folder.
   * For now, only used by Preferences to get the preferences.txt file.
   * @param filename A file inside the settings folder.
   * @return filename wrapped as a File object inside the settings folder
   */
  static public File getSettingsFile(String filename) {
    return new File(getSettingsFolder(), filename);
  }


  static public File getBuildFolder() {
    if (buildFolder == null) {
      String buildPath = Preferences.get("build.path");
      if (buildPath != null) {
        buildFolder = new File(buildPath);

      } else {
        //File folder = new File(getTempFolder(), "build");
        //if (!folder.exists()) folder.mkdirs();
        buildFolder = createTempFolder("build");
        buildFolder.deleteOnExit();
      }
    }
    return buildFolder;
  }


  /**
   * Get the path to the platform's temporary folder, by creating
   * a temporary temporary file and getting its parent folder.
   * <br/>
   * Modified for revision 0094 to actually make the folder randomized
   * to avoid conflicts in multi-user environments. (Bug 177)
   */
  static public File createTempFolder(String name) {
    try {
      File folder = File.createTempFile(name, null);
      //String tempPath = ignored.getParent();
      //return new File(tempPath);
      folder.delete();
      folder.mkdirs();
      return folder;

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }


  static public Map<String, File> getLibraries() {
    return libraries;
  }


  static public String getExamplesPath() {
    return examplesFolder.getAbsolutePath();
  }


  static public List<File> getLibrariesPath() {
    return librariesFolders;
  }


  static public File getToolsFolder() {
    return toolsFolder;
  }


  static public String getToolsPath() {
    return toolsFolder.getAbsolutePath();
  }


  static public File getHardwareFolder() {
    // calculate on the fly because it's needed by Preferences.init() to find
    // the boards.txt and programmers.txt preferences files (which happens
    // before the other folders / paths get cached).
    return getContentFile("hardware");
  }
  
  //Get the core libraries
  static public File getCoreLibraries(String path) {
  	return getContentFile(path);	
  }
  
  static public String getHardwarePath() {
    return getHardwareFolder().getAbsolutePath();
  }
  
  
  static public String getAvrBasePath() {
    String path = getHardwarePath() + File.separator + "tools" +
                  File.separator + "avr" + File.separator + "bin" + File.separator;
    if (Base.isLinux() && !(new File(path)).exists()) {
      return "";  // use distribution provided avr tools if bundled tools missing
    }
    return path;
  }
  
  
  /**
   * Returns the currently selected TargetPlatform.
   * 
   * @return
   */
  static public TargetPlatform getTargetPlatform() {
    String packageName = Preferences.get("target_package");
    String platformName = Preferences.get("target_platform");
    return getTargetPlatform(packageName, platformName);
  }
  
  /**
   * Returns a specific TargetPlatform searching Package/Platform
   * 
   * @param packageName
   * @param platformName
   * @return
   */
  static public TargetPlatform getTargetPlatform(String packageName,
                                                 String platformName) {
    return packages.get(packageName).get(platformName);
  }

  static public TargetPlatform getCurrentTargetPlatformFromPackage(String pack) {
    return getTargetPlatform(pack, Preferences.get("target_platform"));
  }

  static public PreferencesMap getBoardPreferences() {
    TargetPlatform target = getTargetPlatform();
    String board = Preferences.get("board");
    return target.getBoards().get(board);
  }

  static public File getSketchbookFolder() {
    return new File(Preferences.get("sketchbook.path"));
  }


  static public File getSketchbookLibrariesFolder() {
    return new File(getSketchbookFolder(), "libraries");
  }


  static public String getSketchbookLibrariesPath() {
    return getSketchbookLibrariesFolder().getAbsolutePath();
  }
  
  
  static public File getSketchbookHardwareFolder() {
    return new File(getSketchbookFolder(), "hardware");
  }


  protected File getDefaultSketchbookFolder() {
    File sketchbookFolder = null;
    try {
      sketchbookFolder = platform.getDefaultSketchbookFolder();
    } catch (Exception e) { }

    if (sketchbookFolder == null) {
      sketchbookFolder = promptSketchbookLocation();
    }

    // create the folder if it doesn't exist already
    boolean result = true;
    if (!sketchbookFolder.exists()) {
      result = sketchbookFolder.mkdirs();
    }

    if (!result) {
      showError(_("You forgot your sketchbook"),
                _("Arduino cannot run because it could not\n" +
                  "create a folder to store your sketchbook."), null);
    }

    return sketchbookFolder;
  }


  /**
   * Check for a new sketchbook location.
   */
  static protected File promptSketchbookLocation() {
    File folder = null;

    folder = new File(System.getProperty("user.home"), "sketchbook");
    if (!folder.exists()) {
      folder.mkdirs();
      return folder;
    }

    String prompt = _("Select (or create new) folder for sketches...");
    folder = Base.selectFolder(prompt, null, null);
    if (folder == null) {
      System.exit(0);
    }
    return folder;
  }


  // .................................................................


  /**
   * Implements the cross-platform headache of opening URLs
   * TODO This code should be replaced by PApplet.link(),
   * however that's not a static method (because it requires
   * an AppletContext when used as an applet), so it's mildly
   * trickier than just removing this method.
   */
  static public void openURL(String url) {
    try {
      platform.openURL(url);

    } catch (Exception e) {
      showWarning(_("Problem Opening URL"),
                  I18n.format(_("Could not open the URL\n{0}"), url), e);
    }
  }


  /**
   * Used to determine whether to disable the "Show Sketch Folder" option.
   * @return true If a means of opening a folder is known to be available.
   */
  static protected boolean openFolderAvailable() {
    return platform.openFolderAvailable();
  }


  /**
   * Implements the other cross-platform headache of opening
   * a folder in the machine's native file browser.
   */
  static public void openFolder(File file) {
    try {
      platform.openFolder(file);

    } catch (Exception e) {
      showWarning(_("Problem Opening Folder"),
                  I18n.format(_("Could not open the folder\n{0}"), file.getAbsolutePath()), e);
    }
  }


  // .................................................................


  /**
   * Prompt for a fodler and return it as a File object (or null).
   * Implementation for choosing directories that handles both the
   * Mac OS X hack to allow the native AWT file dialog, or uses
   * the JFileChooser on other platforms. Mac AWT trick obtained from
   * <A HREF="http://lists.apple.com/archives/java-dev/2003/Jul/msg00243.html">this post</A>
   * on the OS X Java dev archive which explains the cryptic note in
   * Apple's Java 1.4 release docs about the special System property.
   */
  static public File selectFolder(String prompt, File folder, Frame frame) {
    if (Base.isMacOS()) {
      if (frame == null) frame = new Frame(); //.pack();
      FileDialog fd = new FileDialog(frame, prompt, FileDialog.LOAD);
      if (folder != null) {
        fd.setDirectory(folder.getParent());
        //fd.setFile(folder.getName());
      }
      System.setProperty("apple.awt.fileDialogForDirectories", "true");
      fd.setVisible(true);
      System.setProperty("apple.awt.fileDialogForDirectories", "false");
      if (fd.getFile() == null) {
        return null;
      }
      return new File(fd.getDirectory(), fd.getFile());

    } else {
      JFileChooser fc = new JFileChooser();
      fc.setDialogTitle(prompt);
      if (folder != null) {
        fc.setSelectedFile(folder);
      }
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

      int returned = fc.showOpenDialog(new JDialog());
      if (returned == JFileChooser.APPROVE_OPTION) {
        return fc.getSelectedFile();
      }
    }
    return null;
  }


  // .................................................................


  /**
   * Give this Frame a Processing icon.
   */
  static public void setIcon(Frame frame) {
    // don't use the low-res icon on Mac OS X; the window should
    // already have the right icon from the .app file.
    if (Base.isMacOS()) return;
    
    Image image = Toolkit.getDefaultToolkit().createImage(PApplet.ICON_IMAGE);
    frame.setIconImage(image);
  }


  // someone needs to be slapped
  //static KeyStroke closeWindowKeyStroke;

  /**
   * Return true if the key event was a Ctrl-W or an ESC,
   * both indicators to close the window.
   * Use as part of a keyPressed() event handler for frames.
   */
  /*
  static public boolean isCloseWindowEvent(KeyEvent e) {
    if (closeWindowKeyStroke == null) {
      int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
      closeWindowKeyStroke = KeyStroke.getKeyStroke('W', modifiers);
    }
    return ((e.getKeyCode() == KeyEvent.VK_ESCAPE) ||
            KeyStroke.getKeyStrokeForEvent(e).equals(closeWindowKeyStroke));
  }
  */

  /**
   * Registers key events for a Ctrl-W and ESC with an ActionListener
   * that will take care of disposing the window.
   */
  static public void registerWindowCloseKeys(JRootPane root,
                                             ActionListener disposer) {
    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);

    int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    stroke = KeyStroke.getKeyStroke('W', modifiers);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);
  }


  // .................................................................


  static public void showReference(String filename) {
    File referenceFolder = Base.getContentFile("reference");
    File referenceFile = new File(referenceFolder, filename);
    openURL(referenceFile.getAbsolutePath());
  }

  static public void showGettingStarted() {
    if (Base.isMacOS()) {
      Base.showReference(_("Guide_MacOSX.html"));
    } else if (Base.isWindows()) {
      Base.showReference(_("Guide_Windows.html"));
    } else {
      Base.openURL(_("http://www.arduino.cc/playground/Learning/Linux"));
    }
  }

  static public void showReference() {
    showReference(_("index.html"));
  }


  static public void showEnvironment() {
    showReference(_("Guide_Environment.html"));
  }


  static public void showPlatforms() {
    showReference(_("environment") + File.separator + _("platforms.html"));
  }


  static public void showTroubleshooting() {
    showReference(_("Guide_Troubleshooting.html"));
  }


  static public void showFAQ() {
    showReference(_("FAQ.html"));
  }
  

  // .................................................................


  /**
   * "No cookie for you" type messages. Nothing fatal or all that
   * much of a bummer, but something to notify the user about.
   */
  static public void showMessage(String title, String message) {
    if (title == null) title = _("Message");

    if (commandLine) {
      System.out.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.INFORMATION_MESSAGE);
    }
  }


  /**
   * Non-fatal error message with optional stack trace side dish.
   */
  static public void showWarning(String title, String message, Exception e) {
    if (title == null) title = _("Warning");

    if (commandLine) {
      System.out.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.WARNING_MESSAGE);
    }
    if (e != null) e.printStackTrace();
  }


  /**
   * Show an error message that's actually fatal to the program.
   * This is an error that can't be recovered. Use showWarning()
   * for errors that allow P5 to continue running.
   */
  static public void showError(String title, String message, Throwable e) {
    if (title == null) title = _("Error");

    if (commandLine) {
      System.err.println(title + ": " + message);

    } else {
      JOptionPane.showMessageDialog(new Frame(), message, title,
                                    JOptionPane.ERROR_MESSAGE);
    }
    if (e != null) e.printStackTrace();
    System.exit(1);
  }


  // ...................................................................



  // incomplete
  static public int showYesNoCancelQuestion(Editor editor, String title,
                                            String primary, String secondary) {
    if (!Base.isMacOS()) {
      int result =
        JOptionPane.showConfirmDialog(null, primary + "\n" + secondary, title,
                                      JOptionPane.YES_NO_CANCEL_OPTION,
                                      JOptionPane.QUESTION_MESSAGE);
      return result;
//    if (result == JOptionPane.YES_OPTION) {
//
//    } else if (result == JOptionPane.NO_OPTION) {
//      return true;  // ok to continue
//
//    } else if (result == JOptionPane.CANCEL_OPTION) {
//      return false;
//
//    } else {
//      throw new IllegalStateException();
//    }

    } else {
      // Pane formatting adapted from the Quaqua guide
      // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
      JOptionPane pane =
        new JOptionPane("<html> " +
                        "<head> <style type=\"text/css\">"+
                        "b { font: 13pt \"Lucida Grande\" }"+
                        "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
                        "</style> </head>" +
                        "<b>Do you want to save changes to this sketch<BR>" +
                        " before closing?</b>" +
                        "<p>If you don't save, your changes will be lost.",
                        JOptionPane.QUESTION_MESSAGE);

      String[] options = new String[] {
          "Save", "Cancel", "Don't Save"
      };
      pane.setOptions(options);

      // highlight the safest option ala apple hig
      pane.setInitialValue(options[0]);

      // on macosx, setting the destructive property places this option
      // away from the others at the lefthand side
      pane.putClientProperty("Quaqua.OptionPane.destructiveOption",
                             new Integer(2));

      JDialog dialog = pane.createDialog(editor, null);
      dialog.setVisible(true);

      Object result = pane.getValue();
      if (result == options[0]) {
        return JOptionPane.YES_OPTION;
      } else if (result == options[1]) {
        return JOptionPane.CANCEL_OPTION;
      } else if (result == options[2]) {
        return JOptionPane.NO_OPTION;
      } else {
        return JOptionPane.CLOSED_OPTION;
      }
    }
  }


//if (result == JOptionPane.YES_OPTION) {
  //
//      } else if (result == JOptionPane.NO_OPTION) {
//        return true;  // ok to continue
  //
//      } else if (result == JOptionPane.CANCEL_OPTION) {
//        return false;
  //
//      } else {
//        throw new IllegalStateException();
//      }

  static public int showYesNoQuestion(Frame editor, String title,
                                            String primary, String secondary) {
    if (!Base.isMacOS()) {
      return JOptionPane.showConfirmDialog(editor,
                                           "<html><body>" +
                                           "<b>" + primary + "</b>" +
                                           "<br>" + secondary, title,
                                           JOptionPane.YES_NO_OPTION,
                                           JOptionPane.QUESTION_MESSAGE);
    } else {
      // Pane formatting adapted from the Quaqua guide
      // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
      JOptionPane pane =
        new JOptionPane("<html> " +
                        "<head> <style type=\"text/css\">"+
                        "b { font: 13pt \"Lucida Grande\" }"+
                        "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
                        "</style> </head>" +
                        "<b>" + primary + "</b>" +
                        "<p>" + secondary + "</p>",
                        JOptionPane.QUESTION_MESSAGE);

      String[] options = new String[] {
          "Yes", "No"
      };
      pane.setOptions(options);

      // highlight the safest option ala apple hig
      pane.setInitialValue(options[0]);

      JDialog dialog = pane.createDialog(editor, null);
      dialog.setVisible(true);

      Object result = pane.getValue();
      if (result == options[0]) {
        return JOptionPane.YES_OPTION;
      } else if (result == options[1]) {
        return JOptionPane.NO_OPTION;
      } else {
        return JOptionPane.CLOSED_OPTION;
      }
    }
  }


  /**
   * Retrieve a path to something in the Processing folder. Eventually this
   * may refer to the Contents subfolder of Processing.app, if we bundle things
   * up as a single .app file with no additional folders.
   */
//  static public String getContentsPath(String filename) {
//    String basePath = System.getProperty("user.dir");
//    /*
//      // do this later, when moving to .app package
//    if (PApplet.platform == PConstants.MACOSX) {
//      basePath = System.getProperty("processing.contents");
//    }
//    */
//    return basePath + File.separator + filename;
//  }


  /**
   * Get a path for something in the Processing lib folder.
   */
  /*
  static public String getLibContentsPath(String filename) {
    String libPath = getContentsPath("lib/" + filename);
    File libDir = new File(libPath);
    if (libDir.exists()) {
      return libPath;
    }
//    was looking into making this run from Eclipse, but still too much mess
//    libPath = getContents("build/shared/lib/" + what);
//    libDir = new File(libPath);
//    if (libDir.exists()) {
//      return libPath;
//    }
    return null;
  }
  */

  static public File getContentFile(String name) {
    String path = System.getProperty("user.dir");

    // Get a path to somewhere inside the .app folder
    if (Base.isMacOS()) {
//      <key>javaroot</key>
//      <string>$JAVAROOT</string>
      String javaroot = System.getProperty("javaroot");
      if (javaroot != null) {
        path = javaroot;
      }
    }
    File working = new File(path);
    return new File(working, name);
  }


  /**
   * Get an image associated with the current color theme.
   */
  static public Image getThemeImage(String name, Component who) {
    return getLibImage("theme/" + name, who);
  }


  /**
   * Return an Image object from inside the Processing lib folder.
   */
  static public Image getLibImage(String name, Component who) {
    Image image = null;
    Toolkit tk = Toolkit.getDefaultToolkit();

    File imageLocation = new File(getContentFile("lib"), name);
    image = tk.getImage(imageLocation.getAbsolutePath());
    MediaTracker tracker = new MediaTracker(who);
    tracker.addImage(image, 0);
    try {
      tracker.waitForAll();
    } catch (InterruptedException e) { }
    return image;
  }


  /**
   * Return an InputStream for a file inside the Processing lib folder.
   */
  static public InputStream getLibStream(String filename) throws IOException {
    return new FileInputStream(new File(getContentFile("lib"), filename));
  }


  // ...................................................................


  /**
   * Get the number of lines in a file by counting the number of newline
   * characters inside a String (and adding 1).
   */
  static public int countLines(String what) {
    int count = 1;
    for (char c : what.toCharArray()) {
      if (c == '\n') count++;
    }
    return count;
  }


  /**
   * Same as PApplet.loadBytes(), however never does gzip decoding.
   */
  static public byte[] loadBytesRaw(File file) throws IOException {
    int size = (int) file.length();
    FileInputStream input = new FileInputStream(file);
    byte buffer[] = new byte[size];
    int offset = 0;
    int bytesRead;
    while ((bytesRead = input.read(buffer, offset, size-offset)) != -1) {
      offset += bytesRead;
      if (bytesRead == 0) break;
    }
    input.close();  // weren't properly being closed
    input = null;
    return buffer;
  }



  /**
   * Read from a file with a bunch of attribute/value pairs
   * that are separated by = and ignore comments with #.
   */
  static public HashMap<String,String> readSettings(File inputFile) {
    HashMap<String,String> outgoing = new HashMap<String,String>();
    if (!inputFile.exists()) return outgoing;  // return empty hash

    String lines[] = PApplet.loadStrings(inputFile);
    for (int i = 0; i < lines.length; i++) {
      int hash = lines[i].indexOf('#');
      String line = (hash == -1) ?
        lines[i].trim() : lines[i].substring(0, hash).trim();
      if (line.length() == 0) continue;

      int equals = line.indexOf('=');
      if (equals == -1) {
        System.err.println("ignoring illegal line in " + inputFile);
        System.err.println("  " + line);
        continue;
      }
      String attr = line.substring(0, equals).trim();
      String valu = line.substring(equals + 1).trim();
      outgoing.put(attr, valu);
    }
    return outgoing;
  }


  static public void copyFile(File sourceFile,
                              File targetFile) throws IOException {
    InputStream from =
      new BufferedInputStream(new FileInputStream(sourceFile));
    OutputStream to =
      new BufferedOutputStream(new FileOutputStream(targetFile));
    byte[] buffer = new byte[16 * 1024];
    int bytesRead;
    while ((bytesRead = from.read(buffer)) != -1) {
      to.write(buffer, 0, bytesRead);
    }
    to.flush();
    from.close(); // ??
    from = null;
    to.close(); // ??
    to = null;

    targetFile.setLastModified(sourceFile.lastModified());
  }


  /**
   * Grab the contents of a file as a string.
   */
  static public String loadFile(File file) throws IOException {
    String[] contents = PApplet.loadStrings(file);
    if (contents == null) return null;
    return PApplet.join(contents, "\n");
    }


  /**
   * Spew the contents of a String object out to a file.
   */
  static public void saveFile(String str, File file) throws IOException {
    File temp = File.createTempFile(file.getName(), null, file.getParentFile());
    PApplet.saveStrings(temp, new String[] { str });
    if (file.exists()) {
      boolean result = file.delete();
      if (!result) {
        throw new IOException(
	  I18n.format(
	    _("Could not remove old version of {0}"),
	    file.getAbsolutePath()
	  )
	);
    }
  }
    boolean result = temp.renameTo(file);
    if (!result) {
      throw new IOException(
	I18n.format(
	  _("Could not replace {0}"),
	  file.getAbsolutePath()
	)
      );
    }
  }


  /**
   * Copy a folder from one place to another. This ignores all dot files and
   * folders found in the source directory, to avoid copying silly .DS_Store
   * files and potentially troublesome .svn folders.
   */
  static public void copyDir(File sourceDir,
                             File targetDir) throws IOException {
    targetDir.mkdirs();
    String files[] = sourceDir.list();
    for (int i = 0; i < files.length; i++) {
      // Ignore dot files (.DS_Store), dot folders (.svn) while copying
      if (files[i].charAt(0) == '.') continue;
      //if (files[i].equals(".") || files[i].equals("..")) continue;
      File source = new File(sourceDir, files[i]);
      File target = new File(targetDir, files[i]);
      if (source.isDirectory()) {
        //target.mkdirs();
        copyDir(source, target);
        target.setLastModified(source.lastModified());
      } else {
        copyFile(source, target);
      }
    }
  }


  /**
   * Remove all files in a directory and the directory itself.
   */
  static public void removeDir(File dir) {
    if (dir.exists()) {
      removeDescendants(dir);
      if (!dir.delete()) {
        System.err.println(I18n.format(_("Could not delete {0}"), dir));
      }
    }
  }


  /**
   * Recursively remove all files within a directory,
   * used with removeDir(), or when the contents of a dir
   * should be removed, but not the directory itself.
   * (i.e. when cleaning temp files from lib/build)
   */
  static public void removeDescendants(File dir) {
    if (!dir.exists()) return;

    String files[] = dir.list();
    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || files[i].equals("..")) continue;
      File dead = new File(dir, files[i]);
      if (!dead.isDirectory()) {
        if (!Preferences.getBoolean("compiler.save_build_files")) {
          if (!dead.delete()) {
            // temporarily disabled
	    System.err.println(I18n.format(_("Could not delete {0}"), dead));
          }
        }
      } else {
        removeDir(dead);
        //dead.delete();
      }
    }
  }


  /**
   * Calculate the size of the contents of a folder.
   * Used to determine whether sketches are empty or not.
   * Note that the function calls itself recursively.
   */
  static public int calcFolderSize(File folder) {
    int size = 0;

    String files[] = folder.list();
    // null if folder doesn't exist, happens when deleting sketch
    if (files == null) return -1;

    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(".") || (files[i].equals("..")) ||
          files[i].equals(".DS_Store")) continue;
      File fella = new File(folder, files[i]);
      if (fella.isDirectory()) {
        size += calcFolderSize(fella);
      } else {
        size += (int) fella.length();
      }
    }
    return size;
  }


  /**
   * Recursively creates a list of all files within the specified folder,
   * and returns a list of their relative paths.
   * Ignores any files/folders prefixed with a dot.
   */
  static public String[] listFiles(String path, boolean relative) {
    return listFiles(new File(path), relative);
  }


  static public String[] listFiles(File folder, boolean relative) {
    String path = folder.getAbsolutePath();
    Vector<String> vector = new Vector<String>();
    listFiles(relative ? (path + File.separator) : "", path, vector);
    String outgoing[] = new String[vector.size()];
    vector.copyInto(outgoing);
    return outgoing;
  }


  static protected void listFiles(String basePath,
                                  String path, Vector<String> vector) {
    File folder = new File(path);
    String list[] = folder.list();
    if (list == null) return;

    for (int i = 0; i < list.length; i++) {
      if (list[i].charAt(0) == '.') continue;

      File file = new File(path, list[i]);
      String newPath = file.getAbsolutePath();
      if (newPath.startsWith(basePath)) {
        newPath = newPath.substring(basePath.length());
      }
      vector.add(newPath);
      if (file.isDirectory()) {
        listFiles(basePath, newPath, vector);
      }
    }
  }
}
