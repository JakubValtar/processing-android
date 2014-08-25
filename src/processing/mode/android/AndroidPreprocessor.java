/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2009-10 Ben Fry and Casey Reas

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

package processing.mode.android;

import java.io.IOException;
import java.io.PrintWriter;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import processing.app.*;
import processing.core.PApplet;
import processing.mode.java.preproc.PdeParseTreeListener;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.ProcessingParser;

public class AndroidPreprocessor extends PdePreprocessor {
  Sketch sketch;
  String packageName;

  public AndroidPreprocessor(final Sketch sketch,
                             final String packageName) throws IOException {
    super(sketch.getName());
    this.sketch = sketch;
    this.packageName = packageName;
  }

  @Override
  protected PdeParseTreeListener createListener(CommonTokenStream tokens, String sketchName) {
    return new AndroidParseTreeListener(tokens, sketchName);
  }
  
  private class AndroidParseTreeListener extends PdeParseTreeListener {
    
    protected String sketchWidth;
    protected String sketchHeight;
    protected String sketchRenderer;
    protected String sketchQuality;
    
    protected boolean isSizeValid;
    protected boolean hasSize;
    
    protected boolean isSmoothValid;
    protected boolean hasSmooth;

    protected AndroidParseTreeListener(BufferedTokenStream tokens, String sketchName) {
      super(tokens, sketchName);
    }
    
    @Override
    protected void writeHeader(PrintWriter header) {       
      writePreprocessorComment(header);
      writePackage(header);
      writeImports(header);
      if (mode == Mode.STATIC || mode == Mode.ACTIVE) writeClassHeader(header);
      if (mode == Mode.STATIC) writeStaticSketchHeader(header);
    }
    
    protected void writePackage(PrintWriter header) {
      incLineOffset(); header.println("package " + packageName + ";");
      incLineOffset(); header.println();
    }
    
    @Override
    protected void writeFooter(PrintWriter footer) {
      if (mode == Mode.STATIC) writeStaticSketchFooter(footer);
      if (mode == Mode.STATIC || mode == Mode.ACTIVE) {
        writeExtraDeclarations(footer);
        if (!foundMain) writeMain(footer); 
        writeClassFooter(footer);
      }
    }
    
    protected void writeExtraDeclarations(PrintWriter classBody) {
      if (hasSize && isSizeValid) {
        if (sketchWidth != null) {
          classBody.println();
          classBody.println(indent1 +
              "public int sketchWidth() { return " + sketchWidth + "; }");
        }
        if (sketchHeight != null) {
          classBody.println();
          classBody.println(indent1 +
              "public int sketchHeight() { return " + sketchHeight + "; }");
        }
        if (sketchRenderer != null) {
          classBody.println();
          classBody.println(indent1 +
              "public String sketchRenderer() { return " + sketchRenderer + "; }");
        }
      } else {
        if (reportSketchException(new SketchException("Could not parse the size() command."))) {
          System.err.println("More about the size() command on Android can be");
          System.err.println("found here: http://wiki.processing.org/w/Android");
        }
      }
      
      if (hasSmooth) {
        if (isSmoothValid) {
          if (sketchQuality != null) {
            classBody.println();
            classBody.println(indent1 +
                "public int sketchQuality() { return " + sketchQuality + "; }");
          }
        } else {
          final String message =
              "The smooth level of this applet could not automatically\n" +
              "be determined from your code. Use only a numeric\n" +
              "value (not variables) for the smooth() command.\n" +
              "See the smooth() reference for an explanation.";
            Base.showWarning("Could not find smooth level", message, null);
        }
      }
    }

    public void exitApiSizeFunction(ProcessingParser.ApiSizeFunctionContext ctx) {
      hasSize = true;
      isSizeValid = false;

      if (isFunctionInSetupOrGlobal(ctx)) {
        isSizeValid = true;
        sketchWidth = ctx.getChild(2).getText();
        if (PApplet.parseInt(sketchWidth, -1) == -1 && !sketchWidth.equals("displayWidth")) {
          isSizeValid = false;
          sketchWidth = null;
        }
        sketchHeight = ctx.getChild(4).getText();
        if (PApplet.parseInt(sketchHeight, -1) == -1 && !sketchHeight.equals("displayHeight")) {
          isSizeValid = false;
          sketchHeight = null;
        }      
        if (ctx.getChildCount() > 6) {
          sketchRenderer = ctx.getChild(6).getText();
          if (!(sketchRenderer.equals("P2D") ||
              sketchRenderer.equals("P3D") ||
              sketchRenderer.equals("OPENGL") ||
              sketchRenderer.equals("JAVA2D"))) {
            isSizeValid = false;
            sketchRenderer = null;
          } 
        }
      }

      if (isSizeValid) {
        rewriter.insertBefore(ctx.start, "/* commented out by preprocessor: ");
        rewriter.insertAfter(ctx.stop, " */ print(\"\")"); // noop for debugger
      }
    }

    public void exitApiSmoothFunction(ProcessingParser.ApiSmoothFunctionContext ctx) {
      hasSmooth = true;
      isSmoothValid = false;

      if (isFunctionInSetupOrGlobal(ctx)) {
        isSmoothValid = true;
        sketchQuality = ctx.getChild(2).getText();
        if (PApplet.parseInt(sketchQuality, -1) == -1) {
          isSmoothValid = false;
          sketchQuality = null;
        }
      }

      if (isSmoothValid) {
        rewriter.insertBefore(ctx.start, "/* commented out by preprocessor: ");
        rewriter.insertAfter(ctx.stop, " */ print(\"\")"); // noop for debugger
      }
    }

    protected boolean isFunctionInSetupOrGlobal(ParserRuleContext ctx) {
      // this tree climbing could be avoided if grammar is 
      // adjusted to force context of size()

      System.out.println("depth: " + ctx.depth());
      
      if (ctx.depth() < 6) return false;

      ParserRuleContext testCtx = 
          ctx.getParent() // apiFunction
          .getParent() // expression
          .getParent() // statementExpression
          .getParent() // statement
          .getParent() // blockStatement
          .getParent(); // block or staticProcessingSketch

      if (testCtx instanceof ProcessingParser.StaticProcessingSketchContext) return true;

      if (testCtx.depth() < 2) return false;
      testCtx =
          testCtx.getParent() // methodBody of setup()
          .getParent(); // methodDeclaration of setup()

      if (testCtx.depth() < 3) return false;
      String methodName = testCtx.getChild(1).getText();
      testCtx = testCtx.getParent() // memberDeclaration
          .getParent() // classBodyDeclaration
          .getParent(); // activeProcessingSketch

      return
          methodName.equals("setup") && 
          testCtx instanceof ProcessingParser.ActiveProcessingSketchContext;
    }
  }


  // As of revision 0215 (2.0b7-ish), the default imports are now identical
  // between desktop and Android (to avoid unintended incompatibilities).
  /*
  @Override
  public String[] getCoreImports() {
    return new String[] {
      "processing.core.*",
      "processing.data.*",
      "processing.event.*",
      "processing.opengl.*"
    };
  }


  @Override
  public String[] getDefaultImports() {
    final String prefsLine = Preferences.get("android.preproc.imports");
    if (prefsLine != null) {
      return PApplet.splitTokens(prefsLine, ", ");
    }

    // The initial values are stored in here for the day when Android
    // is broken out as a separate mode.
    
    // In the future, this may include standard classes for phone or
    // accelerometer access within the Android APIs. This is currently living
    // in code rather than preferences.txt because Android mode needs to
    // maintain its independence from the rest of processing.app.
    final String[] androidImports = new String[] {
//      "android.view.MotionEvent", "android.view.KeyEvent",
//      "android.graphics.Bitmap", //"java.awt.Image",
      "java.io.*", // for BufferedReader, InputStream, etc
      //"java.net.*", "java.text.*", // leaving otu for now
      "java.util.*" // for ArrayList and friends
      //"java.util.zip.*", "java.util.regex.*" // not necessary w/ newer i/o
    };

    Preferences.set("android.preproc.imports",
                    PApplet.join(androidImports, ","));

    return androidImports;
  }
  */
}