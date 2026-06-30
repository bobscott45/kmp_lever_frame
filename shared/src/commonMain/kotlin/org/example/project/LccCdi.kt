package org.example.project

object LccCdi {
    /**
     * Generates the OpenLCB/LCC CDI XML string based on the current configuration.
     * This describes the configuration memory space (Space 253) exposed by this node on the network.
     */
    fun generateCdiXml(config: JsonConfig): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<cdi xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://openlcb.org/trunk/prototypes/xml/schema/cdi.xsd\">\n")
        sb.append("  <identification>\n")
        sb.append("    <manufacturer>Robert Scott</manufacturer>\n")
        sb.append("    <model>Kotlin LCC Lever Frame Node</model>\n")
        sb.append("    <hardwareVersion>1.0.0</hardwareVersion>\n")
        sb.append("    <softwareVersion>1.0.0</softwareVersion>\n")
        sb.append("  </identification>\n")
        sb.append("  <segment space=\"253\" origin=\"0\">\n")
        
        // Group: Node Information (Standard ACDI mapping)
        sb.append("    <group>\n")
        sb.append("      <name>Node Information</name>\n")
        sb.append("      <description>User-defined name and description for this node.</description>\n")
        sb.append("      <string size=\"63\">\n")
        sb.append("        <name>Node Name</name>\n")
        sb.append("      </string>\n")
        sb.append("      <string size=\"64\">\n")
        sb.append("        <name>Node Description</name>\n")
        sb.append("      </string>\n")
        sb.append("    </group>\n")
        
        // Group: Global Settings
        sb.append("    <group>\n")
        sb.append("      <name>Global Settings</name>\n")
        sb.append("      <description>High-level settings for the Lever Frame.</description>\n")
        sb.append("      <int size=\"1\">\n")
        sb.append("        <name>Startup Mode</name>\n")
        sb.append("        <description>Lever states on boot.</description>\n")
        sb.append("        <min>0</min>\n")
        sb.append("        <max>1</max>\n")
        sb.append("        <default>1</default>\n")
        sb.append("        <map>\n")
        sb.append("          <relation><property>0</property><value>Safe Default State</value></relation>\n")
        sb.append("          <relation><property>1</property><value>Restore Last State</value></relation>\n")
        sb.append("        </map>\n")
        sb.append("      </int>\n")
        sb.append("      <int size=\"1\">\n")
        sb.append("        <name>Conflict Policy</name>\n")
        sb.append("        <description>What happens when LCC asks to move a locally locked lever.</description>\n")
        sb.append("        <min>0</min>\n")
        sb.append("        <max>2</max>\n")
        sb.append("        <default>0</default>\n")
        sb.append("        <map>\n")
        sb.append("          <relation><property>0</property><value>Strict Local</value></relation>\n")
        sb.append("          <relation><property>1</property><value>Override Allowed</value></relation>\n")
        sb.append("          <relation><property>2</property><value>Accept &amp; Warn (Alarm)</value></relation>\n")
        sb.append("        </map>\n")
        sb.append("      </int>\n")
        sb.append("      <int size=\"4\">\n")
        sb.append("        <name>Display Sleep Timeout</name>\n")
        sb.append("        <description>Time in ms before screen sleeps</description>\n")
        sb.append("        <min>0</min>\n")
        sb.append("        <max>3600000</max>\n")
        sb.append("        <default>60000</default>\n")
        sb.append("      </int>\n")
        sb.append("    </group>\n")
        
        // Optional: Expose lever configuration via CDI if desired
        // If we want to define a replication group for levers, we can do it here.
        val totalLevers = config.tabs.sumOf { it.levers.size }
        if (totalLevers > 0) {
            sb.append("    <group replication=\"$totalLevers\">\n")
            sb.append("      <name>Levers</name>\n")
            sb.append("      <description>Configuration for each lever</description>\n")
            sb.append("      <repname>Lever</repname>\n")
            
            sb.append("      <string size=\"16\">\n")
            sb.append("        <name>Label</name>\n")
            sb.append("      </string>\n")
            
            sb.append("      <string size=\"16\">\n")
            sb.append("        <name>Type</name>\n")
            sb.append("      </string>\n")
            
            sb.append("      <eventid>\n")
            sb.append("        <name>Event Normal</name>\n")
            sb.append("      </eventid>\n")
            
            sb.append("      <eventid>\n")
            sb.append("        <name>Event Reversed</name>\n")
            sb.append("      </eventid>\n")
            
            sb.append("    </group>\n")
        }
        
        sb.append("  </segment>\n")
        sb.append("</cdi>\n")
        
        return sb.toString()
    }
    
    // For when we just want a static CDI array
    val staticCdiXml = """<?xml version="1.0" encoding="UTF-8"?>
<cdi xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://openlcb.org/trunk/prototypes/xml/schema/cdi.xsd">
  <identification>
    <manufacturer>Robert Scott</manufacturer>
    <model>Kotlin LCC Lever Frame Node</model>
    <hardwareVersion>1.0.0</hardwareVersion>
    <softwareVersion>1.0.0</softwareVersion>
  </identification>
  <segment space="253" origin="0">
    <group>
      <name>Node Information</name>
      <description>User-defined name and description for this node.</description>
      <string size="63">
        <name>Node Name</name>
      </string>
      <string size="64">
        <name>Node Description</name>
      </string>
    </group>
    <group>
      <name>Global Settings</name>
      <description>High-level settings for the Lever Frame.</description>
      <int size="1">
        <name>LCC Master Enable</name>
        <description>Whether this node produces LCC events.</description>
        <min>0</min>
        <max>1</max>
        <default>1</default>
        <map>
          <relation><property>0</property><value>Disabled</value></relation>
          <relation><property>1</property><value>Enabled</value></relation>
        </map>
      </int>
      <int size="1">
        <name>Startup Mode</name>
        <description>Lever states on boot.</description>
        <min>0</min>
        <max>1</max>
        <default>1</default>
        <map>
          <relation><property>0</property><value>Safe Default State</value></relation>
          <relation><property>1</property><value>Restore Last State</value></relation>
        </map>
      </int>
      <int size="1">
        <name>Conflict Policy</name>
        <description>What happens when LCC asks to move a locally locked lever.</description>
        <min>0</min>
        <max>2</max>
        <default>0</default>
        <map>
          <relation><property>0</property><value>Strict Local</value></relation>
          <relation><property>1</property><value>Override Allowed</value></relation>
          <relation><property>2</property><value>Accept &amp; Warn (Alarm)</value></relation>
        </map>
      </int>
    </group>
  </segment>
</cdi>"""
}
