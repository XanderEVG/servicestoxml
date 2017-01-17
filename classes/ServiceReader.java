import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.FileInputStream;
import java.util.Properties;



/*****************************************************************************************************************
* Формирует xml файл services.xml содержащий перечень
* опубликованных услуг в РГУ 4.
* 
* URL: http://109.233.229.62:8080/services
* 
* Доступны GET-параметры:
* limit - ограничение количества услуг при выводе в файл. если отсутствует -выводятся все услуги. 
* page - Страница. Применяется совместно с limit, указывает смещение относительно начала на (page-1)*limit услуг.  
* 
* При некорректных параметрах или при их отсутствии выводятся все услуги
*
*****************************************************************************************************************/

public class ServiceReader extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
	
	//Задаем ContentType. Формируем файл. 
        //response.setContentType("text/xml;charset=utf-8");
        response.setContentType("application/octet-stream;charset=utf-8");
        response.setHeader( "Content-Disposition", "attachment; filename=services.xml" );

        PrintWriter pw = response.getWriter();

	//Читаем настройки подключения к БД
        Properties properties = new Properties();
        FileInputStream fis = new FileInputStream("/opt/appserver3/webapps/services/WEB-INF/classes/application.properties");
        properties.load(fis);

        String db_url = properties.getProperty("db.url");
        String db_user = properties.getProperty("db.user");
        String db_pass = properties.getProperty("db.password");


	// Читаем GET параметры, преобразуем в int
        Integer page = -1;
        Integer limit= -1;

        try
        {
            page = Integer.valueOf(request.getParameter("page"));
            limit = Integer.valueOf(request.getParameter("limit"));
        }
        catch (NumberFormatException e)
        {
            page=-1;
            limit=-1;
        }


        Connection con = null;
        Statement st = null;
        ResultSet rs = null;

	//Задаем шапку файла
        pw.println("<?xml version='1.0' encoding='UTF-8' ?>");
        pw.println("<services>");

	//Подключаемся к БД
        try
        {

            try
            {
                Class.forName("org.postgresql.Driver").newInstance();
            }
            catch (InstantiationException ex)
            {
                Logger.getLogger(ServiceReader.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (IllegalAccessException ex)
            {
                Logger.getLogger(ServiceReader.class.getName()).log(Level.SEVERE, null, ex);
            }

            con = DriverManager.getConnection(db_url, db_user, db_pass);


	    //Если заданы GET параметры то подставляем их в запрос. 
            if( page>=0 && limit>=0)
            {
                Integer offset = limit*(page-1);

                if(offset<0)
                    offset=0;

                String sql =    "SELECT service_2.id, service_2.full_name,  service_2.is_function,  organization.id, organization.full_name, meta_dictionary.description, _status_actual.status_text\n" + //, _status_actual.status_text, service_2.status
                                "FROM service_2 \n" +
                                "LEFT OUTER JOIN meta_dictionary ON service_2.adm_level_id=meta_dictionary.id\n" +
                                "LEFT OUTER JOIN organization ON service_2.organization_id=organization.id\n" +
								"LEFT OUTER JOIN _status_actual ON service_2.id=_status_actual.id\n" +
								//"LEFT OUTER JOIN status_dictionary ON status_dictionary.status_key=_status_actual.status_text\n" +
								"WHERE meta_dictionary.description <> 'Федеральный'\n" +
                                "ORDER BY service_2.id LIMIT ? OFFSET ?"; 
								
								
								

				
                PreparedStatement stmt = con.prepareStatement(sql);
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);
                rs = stmt.executeQuery();

            }
            else
            {
                st = con.createStatement();
                rs = st.executeQuery(   "SELECT service_2.id, service_2.full_name, service_2.is_function, organization.id, organization.full_name, meta_dictionary.description, _status_actual.status_text\n" + //, _status_actual.status_text, service_2.status
										"FROM service_2 \n" +
										"LEFT OUTER JOIN meta_dictionary ON service_2.adm_level_id=meta_dictionary.id\n" +
										"LEFT OUTER JOIN organization ON service_2.organization_id=organization.id\n" +
										"LEFT OUTER JOIN _status_actual ON service_2.id=_status_actual.id\n" +
										//"LEFT OUTER JOIN status_dictionary ON status_dictionary.status_key=_status_actual.status_text\n" +
										"WHERE meta_dictionary.description <> 'Федеральный'\n" +
                                        "ORDER BY service_2.id"); 
            }



	    //Вывод инфы об услуге
            while(rs.next())
            {		
					String name = rs.getString(2);
					name = name.replace ('\n', ' ');
					name = name.replace ('\r', ' ');
					name = name.replaceAll("[\\s]{2,}", " ");
					name = name.trim();
                    pw.println("<service>");
                    pw.println("<id>"+rs.getString(1)+"</id>");
                    pw.println("<name>"+name+"</name>");
                    pw.println("<organization_id>"+rs.getString(4)+"</organization_id>");
		    pw.println("<organization>"+rs.getString(5)+"</organization>");
                    pw.println("<adm_level>"+rs.getString(6)+"</adm_level>");
		    pw.println("<status>"+rs.getString(7)+"</status>");
                    String is_f = rs.getString(3).trim();
		    if(is_f.equals("t"))
		    {
			pw.println("<is_func>true</is_func>");
		    }
		    else
                    {
			pw.println("<is_func>false</is_func>");
		    }		   
                    pw.println("</service>");
            }

        }
        catch (SQLException ex)
        {
                 pw.println("<err>");
                 pw.println(ex.getMessage());
                 pw.println("</err>");
        }
        catch (ClassNotFoundException ex)
        {
            Logger.getLogger(ServiceReader.class.getName()).log(Level.SEVERE, null, ex);
        }

	//Закрываем соединение с БД
        finally
        {
            try
            {
                if (rs != null)
                {
                    rs.close();
                }
                if (st != null)
                {
                    st.close();
                }
                if (con != null)
                {
                    con.close();
                }

            }
            catch (SQLException ex)
            {
				Logger.getLogger(ServiceReader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
		
        pw.println("</services>");

    }

}



