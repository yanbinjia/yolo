package tv.ustream.yolo.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bandesz
 */
public class ConfigPattern
{

    private static final Pattern parametersPattern = Pattern.compile("#([a-zA-Z0-9_-]+)#");

    private final String pattern;

    private final List<String> parameters = new ArrayList<String>();

    private final static Map<String, String> globalParameters = new HashMap<String, String>();

    public ConfigPattern(String pattern)
    {
        this.pattern = pattern;
        Matcher matcher = parametersPattern.matcher(pattern);
        while (matcher.find())
        {
            parameters.add(matcher.group(1));
        }
    }

    public static boolean applicable(Object pattern)
    {
        if (!(pattern instanceof String))
        {
            return false;
        }
        Matcher matcher = parametersPattern.matcher((String) pattern);
        return matcher.find();
    }

    @SuppressWarnings("unchecked")
    public static Object replacePatterns(Object data, List<String> validKeys) throws ConfigException
    {
        if (data instanceof Map)
        {
            Map<String, Object> map = ((Map<String, Object>) data);
            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                map.put(entry.getKey(), replacePatterns(entry.getValue(), validKeys));
            }
        }
        if (data instanceof List)
        {
            List<Object> list = (List<Object>) data;
            for (int i = 0; i < list.size(); i++)
            {
                list.set(i, replacePatterns(list.get(i), validKeys));
            }
        }
        if (applicable(data))
        {
            ConfigPattern pattern = new ConfigPattern((String) data);
            if (null != validKeys)
            {
                for (String key : pattern.getParameters())
                {
                    if (!validKeys.contains(key) && !globalParameters.containsKey(key))
                    {
                        throw new ConfigException("#" + key + "# parameter is missing from parser output!");
                    }
                }
            }
            return pattern;
        }
        else
        {
            return data;
        }
    }

    public String applyValues(Map<String, String> values)
    {
        String result = pattern;
        for (String parameter : parameters)
        {

            if (globalParameters.containsKey(parameter))
            {
                result = result.replace("#" + parameter + "#", globalParameters.get(parameter));
            }
            else if (values.containsKey(parameter))
            {
                result = result.replace("#" + parameter + "#", values.get(parameter));
            }
        }
        return result;
    }

    private List<String> getParameters()
    {
        return parameters;
    }

    public static void addGlobalParameter(String key, String value)
    {
        globalParameters.put(key, value);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        ConfigPattern that = (ConfigPattern) o;

        return pattern.equals(that.pattern);

    }

    @Override
    public int hashCode()
    {
        return pattern.hashCode();
    }

    @Override
    public String toString()
    {
        return "ConfigPattern('" + pattern + "')";
    }
}