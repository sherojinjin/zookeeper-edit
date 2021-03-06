/**
 *
 * Copyright (c) 2014 zhoushineyoung. All Rights Reserved.
 *
 * Licensed under the The MIT License (MIT); 
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.martin.zkedit.controller;

import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import com.martin.zkedit.dao.Dao;
import com.martin.zkedit.utils.ServletUtil;
import com.martin.zkedit.utils.ZooKeeperUtil;
import com.martin.zkedit.viewobj.LeafBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = {"/home"}, loadOnStartup = 1)
public class Home extends HttpServlet {

    private final static Logger logger = LoggerFactory.getLogger(Home.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.debug("Home Get Action!");
        try {
            Properties globalProps = (Properties) this.getServletContext().getAttribute("globalProps");
            String zkhosts = globalProps.getProperty("zkServer");
            // String[] zkServerLst = zkServer.split(",");

            Map<String, Object> templateParam = new HashMap<>();
            String zkPath = request.getParameter("zkPath");
            String navigate = request.getParameter("navigate");
            // ZooKeeper zk = ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]);
            ZooKeeper zk = ServletUtil.INSTANCE.getZookeeper(request, response, zkhosts);
            List<String> nodeLst;
            List<LeafBean> leafLst;
            String currentPath, parentPath, displayPath;
            String authRole = (String) request.getSession().getAttribute("authRole");
            if (authRole == null) {
                authRole = ZooKeeperUtil.ROLE_USER;
            }

            if (zkPath == null || zkPath.equals("/")) {
                templateParam.put("zkpath", "/");
                nodeLst = ZooKeeperUtil.INSTANCE.listFolders(zk, "/");
                leafLst = ZooKeeperUtil.INSTANCE.listLeaves(zk, "/", authRole);
                currentPath = "/";
                displayPath = "/";
                parentPath = "/";
            } else {
                templateParam.put("zkPath", zkPath);
                nodeLst = ZooKeeperUtil.INSTANCE.listFolders(zk, zkPath);
                leafLst = ZooKeeperUtil.INSTANCE.listLeaves(zk, zkPath, authRole);
                currentPath = zkPath + "/";
                displayPath = zkPath;
                parentPath = zkPath.substring(0, zkPath.lastIndexOf("/"));
                if (parentPath.equals("")) {
                    parentPath = "/";
                }
            }

            templateParam.put("displayPath", displayPath);
            templateParam.put("parentPath", parentPath);
            templateParam.put("currentPath", currentPath);
            templateParam.put("nodeLst", nodeLst);
            templateParam.put("leafLst", leafLst);
            templateParam.put("breadCrumbLst", displayPath.split("/"));
            templateParam.put("scmRepo", globalProps.getProperty("scmRepo"));
            templateParam.put("scmRepoPath", globalProps.getProperty("scmRepoPath"));
            templateParam.put("navigate", navigate);

            ServletUtil.INSTANCE.renderHtml(request, response, templateParam, "home.html.ftl");

        } catch (KeeperException | InterruptedException | TemplateException ex) {
            ServletUtil.INSTANCE.renderError(request, response, ex.getMessage());
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.debug("Home Post Action!");
        try {
            Properties globalProps = (Properties) this.getServletContext().getAttribute("globalProps");
            Dao dao = new Dao(globalProps);
            String zkServer = globalProps.getProperty("zkServer");
            String[] zkServerLst = zkServer.split(",");

            Map<String, Object> templateParam = new HashMap<>();
            String action = request.getParameter("action");
            String currentPath = request.getParameter("currentPath");
            String displayPath = request.getParameter("displayPath");
            String newProperty = request.getParameter("newProperty");
            String newValue = request.getParameter("newValue");
            String newNode = request.getParameter("newNode");

            String[] nodeChkGroup = request.getParameterValues("nodeChkGroup");
            String[] propChkGroup = request.getParameterValues("propChkGroup");

            String searchStr = request.getParameter("searchStr").trim();
            String authRole = (String) request.getSession().getAttribute("authRole");

            switch (action) {
                case "Save Node":
                    if (!newNode.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {
                        //Save the new node.
                        ZooKeeperUtil.INSTANCE.createFolder(currentPath + newNode, "foo", "bar", ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]));
                        request.getSession().setAttribute("flashMsg", "Node created!");
                        dao.insertHistory((String) request.getSession().getAttribute("authName"), request.getRemoteAddr(), "Creating node: " + currentPath + newNode);
                    }
                    response.sendRedirect("/home?zkPath=" + displayPath);
                    break;
                case "Save Property":
                    if (!newProperty.equals("") && !newValue.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {
                        //Save the new node.
                        ZooKeeperUtil.INSTANCE.createNode(currentPath, newProperty, newValue, ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]));
                        request.getSession().setAttribute("flashMsg", "Property Saved!");
                        if (ZooKeeperUtil.INSTANCE.checkIfPwdField(newProperty)) {
                            newValue = ZooKeeperUtil.INSTANCE.SOPA_PIPA;
                        }
                        dao.insertHistory((String) request.getSession().getAttribute("authName"), request.getRemoteAddr(), "Saving Property: " + currentPath + "," + newProperty + "=" + newValue);
                    }
                    response.sendRedirect("/home?zkPath=" + displayPath);
                    break;
                case "Update Property":
                    if (!newProperty.equals("") && !currentPath.equals("") && authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {
                        //Save the new node.
                        ZooKeeperUtil.INSTANCE.setPropertyValue(currentPath, newProperty, newValue, ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]));
                        request.getSession().setAttribute("flashMsg", "Property Updated!");
                        if (ZooKeeperUtil.INSTANCE.checkIfPwdField(newProperty)) {
                            newValue = ZooKeeperUtil.INSTANCE.SOPA_PIPA;
                        }
                        dao.insertHistory((String) request.getSession().getAttribute("authName"), request.getRemoteAddr(), "Updating Property: " + currentPath + "," + newProperty + "=" + newValue);
                    }
                    response.sendRedirect("/home?zkPath=" + displayPath);
                    break;
                case "Search":
                    Set<LeafBean> searchResult = ZooKeeperUtil.INSTANCE.searchTree(searchStr, ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]), authRole);
                    templateParam.put("searchResult", searchResult);
                    ServletUtil.INSTANCE.renderHtml(request, response, templateParam, "search.html.ftl");
                    break;
                case "Delete":
                    if (authRole.equals(ZooKeeperUtil.ROLE_ADMIN)) {

                        if (propChkGroup != null) {
                            for (String prop : propChkGroup) {
                                List delPropLst = Arrays.asList(prop);
                                ZooKeeperUtil.INSTANCE.deleteLeaves(delPropLst, ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]));
                                request.getSession().setAttribute("flashMsg", "Delete Completed!");
                                dao.insertHistory((String) request.getSession().getAttribute("authName"), request.getRemoteAddr(), "Deleting Property: " + delPropLst.toString());
                            }
                        }
                        if (nodeChkGroup != null) {
                            for (String node : nodeChkGroup) {
                                List delNodeLst = Arrays.asList(node);
                                ZooKeeperUtil.INSTANCE.deleteFolders(delNodeLst, ServletUtil.INSTANCE.getZookeeper(request, response, zkServerLst[0]));
                                request.getSession().setAttribute("flashMsg", "Delete Completed!");
                                dao.insertHistory((String) request.getSession().getAttribute("authName"), request.getRemoteAddr(), "Deleting Nodes: " + delNodeLst.toString());
                            }
                        }

                    }
                    response.sendRedirect("/home?zkPath=" + displayPath);
                    break;
                default:
                    response.sendRedirect("/home");
            }

        } catch (InterruptedException | TemplateException | KeeperException ex) {
            ServletUtil.INSTANCE.renderError(request, response, ex.getMessage());
        }
    }
}
