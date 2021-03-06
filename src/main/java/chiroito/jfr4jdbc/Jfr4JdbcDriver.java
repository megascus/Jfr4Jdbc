package chiroito.jfr4jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import chiroito.jfr4jdbc.event.ConnectEvent;

/**
 * 
 * @author Chihiro Ito
 *
 */
public class Jfr4JdbcDriver implements Driver {

	private static final String JFR4JDBC_URL_PREFIX = "jdbc:jfr";
	private static final int JFR4JDBC_URL_PREFIX_LENGTH = JFR4JDBC_URL_PREFIX.length();

	static {
		try {
			DriverManager.registerDriver(new Jfr4JdbcDriver());
		} catch (SQLException e) {
			throw new RuntimeException("Could not register Jfr4Jdbc.", e);
		}
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 */
	private static final String getDelegateUrl(String url) {
		String delegateUrl = "jdbc" + url.substring(JFR4JDBC_URL_PREFIX_LENGTH);
		return delegateUrl;
	}

	/**
	 * 
	 */
	private Driver delegateJdbcDriver;
	private EventFactory factory = EventFactory.getDefaultEventFactory();
	
	@Override
	public boolean acceptsURL(String url) throws SQLException {

		// Is the url for Jfr4Jdbc.
		if (!url.startsWith(JFR4JDBC_URL_PREFIX)) {
			return false;
		}
		
		// Checking whether the driver is present.
		String delegeteJdbcDriverUrl = Jfr4JdbcDriver.getDelegateUrl(url);
		Driver delegateDriver = DriverManager.getDriver(delegeteJdbcDriverUrl);

		if (delegateDriver == null) {
			return false;
		}

		return true;
	}
		
	@Override
	public Connection connect(String url, Properties info) throws SQLException {

		if (!url.startsWith(JFR4JDBC_URL_PREFIX)) {
			return null;
		}

		// Get a delegated Driver
		String delegeteUrl = Jfr4JdbcDriver.getDelegateUrl(url);
		Driver delegateDriver = DriverManager.getDriver(delegeteUrl);
		if (delegateDriver == null) {
			return null;
		}
		this.delegateJdbcDriver = delegateDriver;

		// Connecting to delegated url and recording connect event.
		ConnectEvent event = factory.createConnectEvent();
		event.setUrl(delegeteUrl);
		event.begin();
		Connection delegatedCon = null;
		try {
			delegatedCon = delegateDriver.connect(delegeteUrl, info);
			if( delegatedCon != null){
				event.setConnectionClass(delegatedCon.getClass());
			}
		} catch (SQLException | RuntimeException e) {
			event.commit();
			throw e;
		}
		if (delegatedCon == null) {
			event.commit();
			throw new SQLException("Invalid driver url: " + url);
		}
		event.setConnectionId(System.identityHashCode(delegatedCon));
		event.commit();

		return new JfrConnection(delegatedCon);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return this.delegateJdbcDriver.getPropertyInfo(url, info);
	}

	@Override
	public int getMajorVersion() {
		return this.delegateJdbcDriver.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return this.delegateJdbcDriver.getMinorVersion();
	}

	@Override
	public boolean jdbcCompliant() {
		return this.delegateJdbcDriver.jdbcCompliant();
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return this.delegateJdbcDriver.getParentLogger();
	}
}