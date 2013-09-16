package tv.ustream.yolo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.NullAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tv.ustream.yolo.config.ConfigException;
import tv.ustream.yolo.config.ConfigPattern;
import tv.ustream.yolo.config.file.Reader;
import tv.ustream.yolo.config.file.ReaderFactory;
import tv.ustream.yolo.module.ModuleChain;
import tv.ustream.yolo.module.ModuleFactory;

/**
 * @author bandesz
 */
public class Yolo
{

    private static final Logger LOG = LoggerFactory.getLogger(Yolo.class);

    private static final PatternLayout CONSOLE_LOG_PATTERN = new PatternLayout("%-6r [%15.15t] %-5p %30.30c - %m%n");

    private static final PatternLayout FILE_LOG_PATTERN = new PatternLayout("%d [%t] %p %c - %m%n");

    private final Options cliOptions = new Options();

    private boolean debug;

    private boolean verbose;

    private String logPath;

    private String configPath;

    private long watchConfigInterval;

    private ModuleChain moduleChain = new ModuleChain(new ModuleFactory());

    private String hostname;

    private Reader configReader;

    public Yolo()
    {
        buildCliOptions();

        setLoggerDefaultOptions();
    }

    private void buildCliOptions()
    {
        cliOptions.addOption("help", false, "print this message");

        cliOptions.addOption("debug", false, "turn on debug mode");

        cliOptions.addOption("verbose", false, "print verbose messages to console");

        Option logOption = new Option("log", true, "log to file");
        logOption.setArgName("path");
        cliOptions.addOption(logOption);

        Option fileOption = new Option("file", true, "path to logfile, wildcards are accepted");
        fileOption.setArgName("path");
        cliOptions.addOption(fileOption);

        Option configOption = new Option("config", true, "path to config file or directory");
        configOption.setArgName("path");
        cliOptions.addOption(configOption);

        cliOptions.addOption("listModules", false, "list available modules");

        cliOptions.addOption("version", false, "show version");

        Option watchConfigIntervalOption = new Option(
                "watchConfigInterval",
                true,
                "check config file periodically and update without stopping, default: 5 sec"
        );
        watchConfigIntervalOption.setArgName("second");
        cliOptions.addOption(watchConfigIntervalOption);

        Option hostnameOption = new Option("hostname", true, "overwrite hostname");
        hostnameOption.setArgName("short hostname");

        cliOptions.addOption(hostnameOption);
    }

    private void setLoggerDefaultOptions()
    {
        org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
        root.addAppender(new NullAppender());
        root.setLevel(Level.INFO);
    }

    private void parseCliOptions(final String[] args) throws Exception
    {
        CommandLine cli;
        try
        {
            cli = new PosixParser().parse(cliOptions, args);
        }
        catch (ParseException exp)
        {
            exitWithError("Error: " + exp.getMessage(), true);
            return;
        }

        if (cli.hasOption("help"))
        {
            printHelp();
            System.exit(0);
        }

        if (cli.hasOption("listModules"))
        {
            ModuleFactory.printAvailableModules();
            System.exit(0);
        }

        if (cli.hasOption("version"))
        {
            printVersion();
            System.exit(0);
        }

        debug = cli.hasOption("debug");

        verbose = cli.hasOption("verbose");

        logPath = cli.getOptionValue("log");

        if (logPath != null && !new File(logPath).isAbsolute())
        {
            exitWithError("log path must be absolute!", false);
            return;
        }

        configPath = cli.getOptionValue("config");

        if (null == configPath || configPath.isEmpty())
        {
            exitWithError("config parameter is missing!", true);
            return;
        }

        if (!new File(configPath).isAbsolute())
        {
            exitWithError("config path must be absolute!", false);
            return;
        }

        watchConfigInterval = TimeUnit.SECONDS.toMillis(
                Integer.parseInt(cli.getOptionValue("watchConfigInterval", "5"))
        );

        hostname = cli.getOptionValue("hostname");
    }

    private void setupLogging()
    {
        org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();

        if (debug)
        {
            root.setLevel(Level.DEBUG);
        }

        if (verbose)
        {
            root.addAppender(new ConsoleAppender(CONSOLE_LOG_PATTERN));
        }

        if (logPath != null)
        {
            try
            {
                root.addAppender(new FileAppender(FILE_LOG_PATTERN, logPath));
            }
            catch (IOException e)
            {
                exitWithError(e.getMessage(), false);
            }
        }
    }

    private void readConfig() throws ConfigException
    {
        try
        {
            configReader = ReaderFactory.createReader(new File(configPath), watchConfigInterval, moduleChain);
            configReader.start();
        }
        catch (IOException e)
        {
            exitWithError("Failed to open configuration file: " + e.getMessage(), false);
            return;
        }
        catch (Exception e)
        {
            exitWithError("Error: " + e.getMessage(), false);
        }
    }

    private void setGlobalParameters() throws Exception
    {
        if (hostname != null)
        {
            ConfigPattern.addGlobalParameter("HOSTNAME", hostname);
        }
        else
        {
            ConfigPattern.addGlobalParameter("HOSTNAME", getHostname());
        }
    }

    private String getHostname() throws IOException
    {
        Process process = Runtime.getRuntime().exec("hostname -s");
        return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
    }

    public void stop()
    {
        moduleChain.stop();

        try
        {
            configReader.stop();
        }
        catch (Exception e)
        {
            exitWithError("Error: " + e.getMessage(), false);
        }

    }

    public void start(String[] args)
    {
        try
        {
            parseCliOptions(args);

            setupLogging();

            setGlobalParameters();

            readConfig();

            addShutdownHook();
        }
        catch (ConfigException e)
        {
            exitWithError(e.getMessage(), false);
        }
        catch (Exception e)
        {
            if (!e.getMessage().isEmpty())
            {
                LOG.error(e.getMessage());
            }
            else
            {
                e.printStackTrace();
            }
        }
    }

    public void addShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                LOG.info("Shutting down..");
                Yolo.this.stop();
            }
        });
    }

    private void printHelp()
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("yolo", cliOptions);
    }

    private void printVersion() throws IOException
    {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("VERSION");
        System.out.println(IOUtils.toString(is, "UTF-8"));
    }

    private void exitWithError(String message, Boolean printHelp)
    {
        LOG.error(message);
        System.console().printf("%s%n", message);
        if (printHelp)
        {
            printHelp();
        }
        System.exit(1);
    }

    public static void main(String[] args)
    {
        Yolo yolo = new Yolo();
        yolo.start(args);
    }

}
