package org.keeber.service;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/admin/*")
public class Admin extends HttpServlet {

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    List<String> path = Constants.getPath(request);
    if (path.isEmpty()) {
      response.sendError(HttpServletResponse.SC_NO_CONTENT);
      return;
    }
    if ("SHUTDOWN".equals(path.get(0))) {
      new Thread(new Runnable() {

        @Override
        public void run() {
          System.exit(0);
        }
      }).start();
      response.getWriter().write("{\"status\":\"BF\"}");
    }
  }



}
