package com.ibm.opensocial.landos;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.wink.json4j.JSONWriter;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

public class BaseServlet extends HttpServlet {
  private static final long serialVersionUID = -7232225273021470838L;
  private static final String CLAZZ = BaseServlet.class.getName();
  private static final Logger LOGGER = Logger.getLogger(CLAZZ);
  
  public static final String DATA_SOURCE = "com.ibm.opensocial.landos.servlets.datasource";
  
  private static DataSource dbSource = null;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    
    if (dbSource == null) {
      try {
        Context initCtx = new InitialContext();
        Context envCtx = (Context)initCtx.lookup("java:comp/env");
        dbSource = (DataSource)envCtx.lookup("jdbc/landos");
      } catch (Exception e) {
        LOGGER.logp(Level.SEVERE, CLAZZ, "init", e.getMessage(), e);
      }
    }
  }
  
  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    req.setAttribute(DATA_SOURCE, dbSource);
    super.service(req, resp);
  }

  protected DataSource getDataSource(HttpServletRequest req) {
    return (DataSource)req.getAttribute(DATA_SOURCE);
  }
  
  protected String getUser(HttpServletRequest req) {
    return req.getHeader("OPENSOCIAL-ID");
  }
  
  protected void close(Object... objects) {
    if (objects != null) {
      for (Object object : objects) {
        if (object != null) {
          if (object instanceof Closeable) {
            try { ((Closeable)object).close(); } catch (IOException ignore) { }
          } else if (object instanceof Statement) {
            try { ((Statement)object).close(); } catch (SQLException ignore) { }
          } else if (object instanceof Connection) {
            try { ((Connection)object).close(); } catch (SQLException ignore) { }
          } else if (object instanceof ResultSet) {
            try { ((ResultSet)object).close(); } catch (SQLException ignore) { }
          } else if (object instanceof JSONWriter) {
            try { ((JSONWriter)object).close(); } catch (Exception ignore) { }
          } else {
            throw new IllegalArgumentException("Cannot handle class:" + object.getClass().getName());
          }
        }
      }
    }
  }
  
  /**
   * Return the number of path segments in a given request.
   * 
   * @param req The request.
   * @return the number of path segments in the given request.
   */
  protected int numSegments(HttpServletRequest req) {
    String path = req.getPathInfo();
    if (path.startsWith("/"))
      path = path.substring(1);
    
    StringTokenizer stok = new StringTokenizer(path, "/");
    int segments = 0;
    while (stok.hasMoreElements()) {
      if (stok.nextToken().equals(""))
        continue;
      segments++;
    }
    
    return segments;
  }
  
  /**
   * Return a certain path segment.
   * 
   * @param req The request.
   * @param segment 0 based index of path segment to get.
   * @return null if the path segment is not specified.
   */
  protected String getPathSegment(HttpServletRequest req, int segment) {
    String path = req.getPathInfo();
    if (Strings.isNullOrEmpty(path))
      path = "";
    if (path.startsWith("/"))
      path = path.substring(1);
    
    StringTokenizer stok = new StringTokenizer(path, "/");
    String candidate = null;
    for (;segment >= 0 && stok.hasMoreElements(); segment--) {
      // paths like /some//path -> /some/path
      candidate = stok.nextToken();
      while ("".equals(candidate) && stok.hasMoreElements()) {
        candidate = stok.nextToken();
      }
    }
    if (segment > -1)
      return null;
    else {
      try {
        return URLDecoder.decode(candidate, Charsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        LOGGER.logp(Level.SEVERE, CLAZZ, "getPathSegment", e.getMessage(), e);
        return null;
      }
    }
  }

  /**
   * Sets headers to have no cache and a type of application/json
   * 
   * @param res
   *          The response object to set the headers on
   */
  protected void setCacheAndTypeHeaders(HttpServletResponse res) {
    res.setHeader("Cache-Control", "no-cache");
    res.setContentType("application/json");
  }

  /**
   * @param res
   * @return
   * @throws IOException
   */
  protected JSONWriter getJSONWriter(HttpServletResponse res) throws IOException {
    return new JSONWriter(res.getWriter());
  }
  
  protected String getEEUrl(HttpServletRequest req) {
    //TODO Get hostname programmatically.
    return "http://kargath.notesdev.ibm.com" + req.getContextPath() + "/LandosApp.xml";
  }
  
  /**
   * @param user Users are in the form of `domain:user` 
   *             Gross hack until domino gets user apis working.
   * @return email address for user.
   */
  protected String getEmailForUser(String user) {
    String[] parts = user.split(":");
    return parts[1] + "@" + parts[0];
  }
  
  /**
   * @param user 
   * @return true if the user is an admin.
   */
  protected boolean isAdmin(HttpServletRequest req, String user) throws SQLException {
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet results = null;
    
    boolean isAdmin = false;
    try {
      conn = getDataSource(req).getConnection();
      stmt = conn.prepareStatement("SELECT * FROM `subscribed` WHERE `user`=? AND `admin`=1");
      stmt.setString(1, user);
      results = stmt.executeQuery();
      if (results.first())
        isAdmin = true;
    } finally {
      close(results, stmt, conn);
    }
    
    return isAdmin;
  }
}

