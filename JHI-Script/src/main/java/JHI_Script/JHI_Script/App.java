package JHI_Script.JHI_Script;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import com.enonic.harvest.harvestclient.HarvestClient;
import com.enonic.harvest.harvestclient.HarvestClientFactory;
import com.enonic.harvest.harvestclient.models.DayEntry;
import com.enonic.harvest.harvestclient.models.User;
import com.enonic.harvest.harvestclient.parameters.GetDayEntriesByUserParameters;
import com.lesstif.jira.issue.Issue;
import com.lesstif.jira.issue.WorklogElement;
import com.lesstif.jira.services.IssueService;

import org.codehaus.jackson.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

	private static Logger logger = LoggerFactory.getLogger(App.class);
	private static DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy");
	private static Date dateAppend = new Date();
	private static String DOMAIN_NAME = "authx"; 
	private static String USERNAME = ""; 
	private static String PASSWORD = "";
	private static String FILE_PATH = "/Users/user/Documents/" + "TESTING-ADMIN2 " + dateFormat.format(dateAppend)
			+ "-worklog.csv";
	private static String REGEX = "(JHI-\\d+)"; //comment out these two lines to switch between JHI searching or all IssueIDs
	// private static String REGEX = "([A-Z1-9]+-\\d+)";

	public static void main(String[] args)
			throws ConfigurationException, IOException, org.apache.commons.configuration.ConfigurationException {
		System.out.println("Authenticating...");
		HarvestClientFactory factory = new HarvestClientFactory();
		HarvestClient client = factory.create(DOMAIN_NAME, USERNAME, PASSWORD);
		List<User> users = client.getUsers();
		loopThroughUsers(client, users);
	}

	private static void loopThroughUsers(HarvestClient client, List<User> users)
			throws org.apache.commons.configuration.ConfigurationException, IOException {
		int index1 = 0;		
		for (User user : users) {
			Calendar c1 = new GregorianCalendar();
			Calendar c2 = new GregorianCalendar();
			c2.add(Calendar.DAY_OF_MONTH, -1); // Amount of days to go back in time to get
			Date todaysDate = c1.getTime();
			Date dateFromWeekAgo = c2.getTime();
			Long userId = users.get(index1).getId();
			GetDayEntriesByUserParameters getDayEntries = new GetDayEntriesByUserParameters();
			getDayEntries.fromDate = dateFromWeekAgo;
			getDayEntries.toDate = todaysDate;
			getDayEntries.userId = userId;
			getDayEntries.updatedSince = dateFromWeekAgo;
			List<DayEntry> dayEntrys = client.getDayEntriesByUser(getDayEntries);
			IssueService is = new IssueService();
			WorklogElement worklog = new WorklogElement();
			Issue issue = new Issue();
			loopThroughDayEntries(users, index1, dayEntrys, is, worklog, issue);
			index1++;
		}
	}

	private static void loopThroughDayEntries(List<User> users, int index1, List<DayEntry> dayEntrys, IssueService is,
			WorklogElement worklog, Issue issue) throws IOException {
		int index2 = 0;
		for (DayEntry dayEntry : dayEntrys) {
			BigDecimal bigDecimal = ((dayEntrys.get(index2).getHours()));
			worklog.setComment(dayEntrys.get(index2).getNotes() + " @author: " + users.get(index1).getFirstName() + " "
					+ users.get(index1).getLastName());
			worklog.setTimeSpentSeconds(convertTimeToSecs(bigDecimal));

			String comment = (dayEntrys.get(index2).getNotes());
			Matcher matcher;
			Date date = dayEntrys.get(index2).getCreatedAt();
			String dateString = date.toString();

			searchForIssueID(users, index1, dayEntrys, is, worklog, issue, index2, comment, dateString);
			index2++;
		}
	}

	private static void searchForIssueID(List<User> users, int index1, List<DayEntry> dayEntrys, IssueService is,
			WorklogElement worklog, Issue issue, int index2, String comment, String dateString) throws IOException {
		Matcher matcher;
		try {
			Pattern pattern = Pattern.compile(REGEX);
			matcher = pattern.matcher(comment);
			if (matcher.find()) {
				String issueID = matcher.group();
				logger.debug("REGEX WORKED AND ISSUEID IS: " + issueID);
				issue.setId(issueID);
				is.postWorklog(worklog, issue);
				logger.debug("Added " + users.get((index1)).getFirstName() + " " + users.get((index1)).getLastName()
						+ " to worklog with Issue: " + issueID);
			} else {
				logger.debug("Comment written by: " + users.get(index1).getFirstName() + " "
						+ users.get(index1).getLastName()
						+ " did not contain the right syntax to post to Jira worklog");
			}
		} catch (JsonProcessingException jpex) {
			logger.error("Blew up parsing JSON");
			logger.info(jpex.getMessage());

		} catch (IOException ioex) {
			logger.error("Could not read/write to file");
			logger.info(ioex.getMessage());
		} catch (NullPointerException e) {
			logger.debug(
					"Comment is null for: " + users.get(index1).getFirstName() + " " + users.get(index1).getLastName());
		} catch (Exception e) {
			// Throws this when issueID is not found after finding exception name
			logger.debug("Error. Did not successfully add" + users.get((index1)).getFirstName() + " "
					+ users.get((index1)).getLastName() + " to worklog.");
		} finally {

			createLocalWorkLog(dateString, users.get(index1).getFirstName() + " " + users.get(index1).getLastName(),
					dayEntrys.get(index2).getNotes(), FILE_PATH);

		}
	}

	// if filename is the same then new entries are appended to end
	private static void createLocalWorkLog(String dateString, String name, String notes, String FILE_PATH)
			throws IOException {
		FileWriter writer = new FileWriter(FILE_PATH, true);
		writer.append(dateString);
		writer.append(',');
		writer.append(name);
		writer.append(',');
		writer.append(notes);
		writer.append('\n');
		writer.flush();
		writer.close();
	}

	private static int convertTimeToSecs(BigDecimal bigDecimal) {
		BigDecimal value = bigDecimal;
		double intValue = value.floatValue();
		double totalSeconds = intValue * 3600;
		totalSeconds = Math.round(totalSeconds / 60);
		totalSeconds = totalSeconds * 60;
		int convertTotalSecs = (int) (totalSeconds);
		return convertTotalSecs;
	}
}
