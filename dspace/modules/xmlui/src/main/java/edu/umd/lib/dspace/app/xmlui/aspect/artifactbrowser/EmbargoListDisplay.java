/*
 * Copyright (c) 2009 The University of Maryland. All Rights Reserved.
 *
 */
package edu.umd.lib.dspace.app.xmlui.aspect.artifactbrowser;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.excalibur.source.SourceValidity;
import org.apache.log4j.Logger;
import org.dspace.app.statistics.StatisticsLoader;
import org.dspace.app.xmlui.aspect.artifactbrowser.StatisticsViewer;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.DSpaceValidity;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Row;
import org.dspace.app.xmlui.wing.element.Table;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.ConfigurationManager;
import org.dspace.storage.rdbms.TableRow;
import org.xml.sax.SAXException;

/**
 * Transformer to display Embargo List data in XML UI.
 */
public class EmbargoListDisplay extends AbstractDSpaceTransformer implements
CacheableProcessingComponent
{
    private static final Logger log = Logger.getLogger(StatisticsViewer.class);

    private static final Message T_dspace_home = message("xmlui.general.dspace_home");

    private static final Message T_choose_report = message("xmlui.ArtifactBrowser.StatisticsViewer.choose_month");

    private static final Message T_page_title = message("xmlui.ArtifactBrowser.EmbargoList.title");

    private static final SimpleDateFormat sdfDisplay = new SimpleDateFormat(
            "MM'/'yyyy");

    private static final SimpleDateFormat sdfLink = new SimpleDateFormat(
            "yyyy'-'M");

    private boolean initialised = false;

    private String reportDate = null;

    private SourceValidity validity;

    /**
     * Get the caching key for this report.
     */
    @Override
    public Serializable getKey()
    {
        initialise();

        if (reportDate != null)
        {
            return reportDate;
        }

        return "general";
    }

    /**
     * Generate the validity for this cached entry.
     */
    @Override
    public SourceValidity getValidity()
    {
        if (validity == null)
        {
            try
            {
                initialise();
                boolean showReport = ConfigurationManager
                        .getBooleanProperty("report.public");

                // If the report isn't public
                if (!showReport)
                {
                    try
                    {
                        // Administrators can always view reports
                        showReport = AuthorizeManager.isAdmin(context);
                    }
                    catch (SQLException sqle)
                    {
                        log.error("Unable to check for administrator", sqle);
                    }
                }

                // Only generate a validity if the report is visible
                if (showReport)
                {
                    File analysisFile = null;

                    // Get a file for the report data
                    if (reportDate != null)
                    {
                        analysisFile = StatisticsLoader
                                .getAnalysisFor(reportDate);
                    }
                    else
                    {
                        analysisFile = StatisticsLoader.getGeneralAnalysis();
                    }

                    if (analysisFile != null)
                    {
                        // Generate the validity based on the properties of the
                        // report data file
                        DSpaceValidity newValidity = new DSpaceValidity();
                        newValidity.add(Long.toString(analysisFile
                                .lastModified()));
                        newValidity.add("-");
                        newValidity.add(Long.toString(analysisFile.length()));
                        validity = newValidity.complete();
                    }
                }
            }
            catch (Exception e)
            {

            }
        }

        return validity;
    }

    /**
     * Add additional navigation options. This is to allow selection of a
     * monthly report
     *
     * @param options
     * @throws SAXException
     * @throws WingException
     * @throws UIException
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    // @Override
    // public void addOptions(Options options) throws SAXException,
    // WingException,
    // UIException, SQLException, IOException, AuthorizeException
    // {
    // Date[] monthlyDates = StatisticsLoader.getMonthlyAnalysisDates();
    //
    // if (monthlyDates != null && monthlyDates.length > 0)
    // {
    // List statList = options.addList("statsreports");
    // statList.setHead(T_choose_report);
    //
    // HashMap<String, String> params = new HashMap<String, String>();
    // for (Date date : monthlyDates)
    // {
    // params.put("date", sdfLink.format(date));
    // statList.addItemXref(super.generateURL("statistics", params),
    // sdfDisplay.format(date));
    // }
    // }
    // }

    /**
     * Add title, etc. metadata
     *
     * @param pageMeta
     * @throws SAXException
     * @throws WingException
     * @throws UIException
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    @Override
    public void addPageMeta(PageMeta pageMeta) throws SAXException,
    WingException, UIException, SQLException, IOException,
    AuthorizeException
    {
        initialise();

        pageMeta.addMetadata("title").addContent(T_page_title);
        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
        pageMeta.addTrail().addContent(T_page_title);
    }

    /**
     * Output the body of the report
     *
     * @param body
     * @throws SAXException
     * @throws WingException
     * @throws UIException
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    @Override
    public void addBody(Body body) throws SAXException, WingException,
    UIException, SQLException, IOException, AuthorizeException
    {
        initialise();
        boolean publicise = ConfigurationManager
                .getBooleanProperty("report.public");

        // Check that the reports are either public, or user is an administrator
        if (!publicise && !AuthorizeManager.isAdmin(context))
        {
            throw new AuthorizeException();
        }

        // Create the renderer for the results
        Division div = body.addDivision("embargo-list", "primary");

        List download = div.addList("download");
        download.addItemXref(contextPath + "/embargo-list/csv", "Download CSV");

        Request request = ObjectModelHelper.getRequest(objectModel);

        ArrayList<TableRow> rowList = (ArrayList<TableRow>) EmbargoListHelper
                .getEmbargoList(request);
        Table table = div.addTable("list-of-embargoes", 22, 9);

        Row header = table.addRow(Row.ROLE_HEADER);

        header.addCell().addContent("Handle");
        header.addCell().addContent("Item ID");
        header.addCell().addContent("Bitstream ID");
        header.addCell().addContent("Title");
        header.addCell().addContent("Advisor");
        header.addCell().addContent("Author");
        header.addCell().addContent("Department");
        header.addCell().addContent("Type");
        header.addCell().addContent("End Date");

        for (TableRow row : rowList)
            // for (int i = 0; i < 20; i++)
        {
            // TableRow row = rowList.get(i);

            Row tableRow = table.addRow();

            tableRow.addCell().addXref(
                    contextPath + "/handle/" + row.getStringColumn("handle"),
                    row.getStringColumn("handle"));
            tableRow.addCell().addContent(row.getIntColumn("item_id"));
            tableRow.addCell().addContent(row.getIntColumn("bitstream_id"));
            tableRow.addCell().addContent(row.getStringColumn("title"));
            tableRow.addCell().addContent(row.getStringColumn("advisor"));
            tableRow.addCell().addContent(row.getStringColumn("author"));
            tableRow.addCell().addContent(row.getStringColumn("department"));
            tableRow.addCell().addContent(row.getStringColumn("type"));
            try
            {
                tableRow.addCell().addContent(
                        row.getDateColumn("end_date").toString());
            }
            catch (NullPointerException e)
            {
                tableRow.addCell().addContent("null");
            }

        }
        // tri.close();

    }

    /**
     * Initialise the member variables from the request
     */
    private void initialise()
    {
        if (!initialised)
        {
            Request request = ObjectModelHelper.getRequest(objectModel);
            reportDate = request.getParameter("date");

            initialised = true;
        }
    }

    /**
     * Clear the member variables so that the instance can be reused
     */
    @Override
    public void recycle()
    {
        initialised = false;
        reportDate = null;
        validity = null;
        super.recycle();
    }

}
