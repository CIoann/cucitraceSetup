package edu.ysu.itrace;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class dbtest {

  public static void main(String[] args) {
    try {
      int sce_gaze_id;
      Class.forName("com.mysql.jdbc.Driver");   
      java.sql.Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/iTrace?autoReconnect=true&useSSL=false","root","1234");
      if (conn!=null ){
    	  int rs1;
          System.out.println("WE ARE CONNECTED");
          String statement = "INSERT INTO session_info" 
             + "(session_ID,session_purpose,session_descrip,developer_username, "
             + "developer_name) VALUES ( "
             + "'" + "newkokos2" + "',"
             + "'" + "newkokos2" + "',"
             + "'" + "newkokos2" + "',"
             + "'" + "newkokos2" + "',"
             + "'" + "newkokos2" + "')";
          //   + "'" + String.format("%d",this.screenRect.getWidth()) + ","
          //   + "'" + String.format("%d",this.screenRect.getHeight()) + ");";
          rs1 = conn.createStatement().executeUpdate(statement);
          System.out.println(statement);    
    	  
      }else
      {
    	  System.out.print("problem");
      }
      
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    
  }
}
