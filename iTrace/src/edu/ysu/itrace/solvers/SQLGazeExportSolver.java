package edu.ysu.itrace.solvers;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.activation.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.swing.UIManager;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import edu.ysu.itrace.Gaze;
import edu.ysu.itrace.ScEntity;
import edu.ysu.itrace.gaze.IGazeResponse;
import edu.ysu.itrace.gaze.IStyledTextGazeResponse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
public class SQLGazeExportSolver implements IFileExportSolver, EventHandler {

  private Dimension screenRect;
  private String sessionID;
  private String sessionPurpose;
  private String sessionDescrip;
  private String devName;
  private String devUsername;
  private IEventBroker eventBroker;
  private String outFile;
  Connection conn;
  
  public SQLGazeExportSolver() {
    UIManager.put("swing.boldMetal", new Boolean(false)); //make UI font plain
    eventBroker = PlatformUI.getWorkbench().getService(IEventBroker.class);
  }

  
  /**
   * Any initialization work with side effects, such as opening files. This
   * method should very probably be called before calling process or dispose.
   */
  public void init() {
    screenRect = Toolkit.getDefaultToolkit().getScreenSize();
    outFile = "Set";
  //  System.out.println("init is called HAHASDHASHDHASDHASDHASD");

  }

  public void setSessionInfo() {
    try {
      System.out.println("Connecting to DB");
      Class.forName("com.mysql.jdbc.Driver");   
      this.conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/iTrace","root","1234");
      // Insert session info into db.
      int rs;
      System.out.println("WE ARE CONNECTED");
      String statement = "INSERT INTO session_info" 
         + "(session_ID,session_purpose,session_descrip,developer_username, "
         + "developer_name) VALUES ( "
         + "'" + this.sessionID + "',"
         + "'" + this.sessionPurpose + "',"
         + "'" + this.sessionDescrip + "',"
         + "'" + this.devUsername + "',"
         + "'" + this.devName + "')";
      //   + "'" + String.format("%d",this.screenRect.getWidth()) + ","
      //   + "'" + String.format("%d",this.screenRect.getHeight()) + ");";
      rs = conn.createStatement().executeUpdate(statement);
   //  System.out.println(statement);    
    }

    catch(SQLException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
  }
  
  /**
   * Called to process new gazes.
   */
  public void process(IGazeResponse response) {
    int screenX =
            (int) (screenRect.width * response.getGaze().getX());
    int screenY =
            (int) (screenRect.height * response.getGaze().getY());
      
    Gaze gaze = response.getGaze();
    String statementInsert = "INSERT INTO gazes (gaze_name, gaze_type, gaze_session_id, x, y,"
        + "left_validation, right_validation, left_pupil_diameter,"
        + "right_pupil_diameter, timestamp, session_time, tracker_time,"
        + "system_time, nano_time";
    
    String statementValues = " VALUES ('" 
        + response.getName() + "','"
        + response.getGazeType() + "','"
        + this.sessionID + "','"
        + screenX + "','"
        + screenY + "','"
        + gaze.getLeftValidity() + "','"
        + gaze.getRightValidity() + "','"
        + gaze.getLeftPupilDiameter() + "','"
        + gaze.getRightPupilDiameter() + "','"
        + gaze.getTimestamp() + "','"
        + gaze.getSessionTime() + "','"
        + gaze.getTrackerTime() + "','"
        + gaze.getSystemTime() + "','"
        + gaze.getNanoTime() + "'";
    
    String nostyle = statementInsert + ")" + statementValues + ");";
    
    // gazes
    if (response instanceof IStyledTextGazeResponse) {
      IStyledTextGazeResponse styledResponse =
          (IStyledTextGazeResponse) response;
      statementInsert += ", path, line_height, font_height, line," 
          + "col, line_base_x, line_base_y";
      
      statementValues += ",'" + styledResponse.getPath() + "','"
          + styledResponse.getLineHeight() + "','"
          + styledResponse.getFontHeight() + "','"
          + styledResponse.getLine() + "','"
          + styledResponse.getCol() + "','"
          + styledResponse.getLineBaseX() + "','"
          + styledResponse.getLineBaseY() + "'";
      
      String style = statementInsert + ")" + statementValues + ");";
      
      try {
      //  System.out.println("style");
       // System.out.println(style);
        this.conn.createStatement().executeUpdate(style);
        
     //   System.out.println(style);
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
   
    // sces  
    int depth = 0;
    String sce_gaze_id = "";
    try {
     ResultSet rs = this.conn.createStatement().executeQuery("SELECT max(gaze_id) FROM gazes WHERE gaze_session_id='" + sessionID +"';");
     rs.next();
     sce_gaze_id = rs.getString("max(gaze_id)");
     //System.out.println(sce_gaze_id);
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    for (ScEntity sce : styledResponse.getSCEs()) {
     
      String statementSCE = "INSERT INTO sces (sce_gaze_id, " 
          + "sce_name, sce_type, how, total_length,"
          + "start_line, end_line, start_col, end_col, depth)" 
          + "VALUES ('" + sce_gaze_id + "','"
          + sce.getName() +"','"
          + sce.type.toString() + "','"
          + sce.how.toString() + "','"
          + sce.totalLength + "','"
          + sce.startLine + "','"
          + sce.endLine + "','"
          + sce.startCol + "','"
          + sce.endCol + "','"
          + depth + "');";
        depth++;
        
       try {
    	   System.out.println(""+statementSCE);
        this.conn.createStatement().executeUpdate(statementSCE);
        
     //   System.out.println(statementSCE);
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      }
    }
    else {
      try {
       // System.out.println("nostyle");
       // System.out.println(nostyle);
        this.conn.createStatement().executeUpdate(nostyle);
      } catch (SQLException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

      
  }

  
  public String friendlyName() {
    return "SQL Gaze export";
  }

  /**
   * Configure the export filename.
   */

  public void config(String sessionID, String devUsername) {
    // TODO Auto-generated method stub
    
  }

  public void configSQL(String sessionID, String devUsername,
      String devName, String sessionDescrip, String sessionPurpose){
    this.sessionID = sessionID;
    this.devUsername = devUsername;
    this.devName = devName;
    this.devUsername = devUsername;
    this.sessionDescrip = sessionDescrip;
    this.sessionPurpose = sessionPurpose;
  }
  
  /**
   * Launch dialog to display the export filename.
   */
  public void displayExportFile() {
    
  }



  /**
   * Frees any resources. It is very likely a bad idea to process new data
   * after calling dispose. Not sure if we need this, either.
   */
  public void dispose() {
    this.conn = null;
  }



  @Override
  public void handleEvent(Event event) {
    // TODO Auto-generated method stub
    if(outFile == null) this.init();
    String[] propertyNames = event.getPropertyNames();
    IGazeResponse response = (IGazeResponse)event.getProperty(propertyNames[0]);
    this.process(response);

  }


  @Override
  public String getFilename() {
    // TODO Auto-generated method stub
    return null;
  }
}


