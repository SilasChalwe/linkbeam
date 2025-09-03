import React, { useState, useEffect } from 'react';
import { View, Text, Button, StyleSheet, ScrollView, Alert, Platform, Linking, ActivityIndicator, NativeModules } from 'react-native';
import * as FileSystem from 'expo-file-system';

const { SimpleHttpServer, NetworkHelper } = NativeModules;

const ServerComponent = () => {
  const [isRunning, setIsRunning] = useState(false);
  const [serverUrl, setServerUrl] = useState('');
  const [wifiIp, setWifiIp] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [logs, setLogs] = useState([]);

  const PORT = 8000;

  useEffect(() => {
    getWifiIp();
    setupServerDirectory();
    
    return () => {
      if (isRunning) {
        stopServer();
      }
    };
  }, []);

  const addLog = (message) => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs(prevLogs => [`[${timestamp}] ${message}`, ...prevLogs.slice(0, 9)]);
  };

  const getWifiIp = async () => {
    try {
      if (NetworkHelper) {
        // Use native module to get WiFi IP
        const ip = await NetworkHelper.getWifiIpAddress();
        setWifiIp(ip);
        addLog(`WiFi IP: ${ip}`);
      } else {
        addLog('Native NetworkHelper not available - using fallback');
        setWifiIp('192.168.x.x'); // Fallback
      }
    } catch (error) {
      addLog(`Error getting WiFi IP: ${error.message}`);
      setWifiIp('localhost');
    }
  };

  const setupServerDirectory = async () => {
    try {
      const serverDir = FileSystem.documentDirectory + 'www/';
      const dirInfo = await FileSystem.getInfoAsync(serverDir);
      
      if (!dirInfo.exists) {
        await FileSystem.makeDirectoryAsync(serverDir, { intermediates: true });
      }
      
      // Create simple index.html
      const indexContent = `<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Link •••••• -  </title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #2196F3; text-align: center; }
        .status { background: #e8f5e9; padding: 15px; border-radius: 5px; margin: 20px 0; }
        .ip { font-family: monospace; font-size: 18px; font-weight: bold; color: #333; background: #f0f0f0; padding: 10px; border-radius: 5px; text-align: center; }
    </style>
</head>
<body>
    <div class="container">
        <h1> Running</h1>
        <div class="status">
            <p><strong>Platform:</strong> ${Platform.OS}</p>
            <p><strong>Access URL:</strong></p>
            <div class="ip">http://${wifiIp}</div>
        </div>
        <p>Server is accessible from any device on the same WiFi network.</p>
        <p>You can add files to the server directory and access them through this URL.</p>
    </div>
</body>
</html>`;

      await FileSystem.writeAsStringAsync(serverDir + 'index.html', indexContent);
      addLog('Server directory ready');
    } catch (error) {
      addLog(`Setup error: ${error.message}`);
    }
  };

  const startServer = async () => {
    if (!SimpleHttpServer) {
      Alert.alert(
        'Native Module Required',
        'To use native HTTP server:\n\n1. Add native modules\n2. Build development build\n3. Run: npx expo run:android',
        [{ text: 'OK' }]
      );
      return;
    }

    try {
      setIsLoading(true);
      addLog('Starting HTTP server...');

      const serverDir = (FileSystem.documentDirectory + 'www/').replace('file://', '');
      
      // Start native HTTP server on all interfaces (0.0.0.0)
      const result = await SimpleHttpServer.start(PORT, serverDir);
      
      if (result.success) {
        const url = `http://${wifiIp}:${PORT}`;
        setServerUrl(url);
        setIsRunning(true);
        addLog(`Server started on ${url}`);
        
        Alert.alert(
          'Server Started! ',
          `Access from any device on WiFi:\n${url}`,
          [
            { text: 'Open', onPress: () => Linking.openURL(url) },
            { text: 'OK' }
          ]
        );
      } else {
        throw new Error(result.error || 'Failed to start server');
      }
    } catch (error) {
      addLog(`Error: ${error.message}`);
      Alert.alert('Error', `Failed to start server:\n${error.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const stopServer = async () => {
    try {
      if (SimpleHttpServer) {
        await SimpleHttpServer.stop();
      }
      setIsRunning(false);
      setServerUrl('');
      addLog('Server stopped');
    } catch (error) {
      addLog(`Stop error: ${error.message}`);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>     LINKBEAM</Text>
        <Text style={styles.subtitle}>Open linkbeam desktop to connect</Text>
      </View>

      <View style={styles.statusContainer}>
        <View style={styles.statusRow}>
          <Text style={styles.label}>Status:</Text>
          <View style={[styles.indicator, { backgroundColor: isRunning ? '#4CAF50' : '#f44336' }]}>
            <Text style={styles.indicatorText}>{isRunning ? 'RUNNING' : 'STOPPED'}</Text>
          </View>
        </View>
        <View style={styles.statusRow}>
          <Text style={styles.label}>WiFi IP:</Text>
          <Text style={styles.ipText}>{wifiIp}</Text>
        </View>
      </View>

      {isRunning ? (
        <View style={styles.runningContainer}>
          <Text style={styles.urlLabel}>Access from any device:</Text>
          <Text style={styles.urlText}>{serverUrl}</Text>
          <Text style={styles.instruction}>
            Other devices on the same WiFi can access this URL
          </Text>
          <View style={styles.buttonRow}>
            <Button title="Stop Server" onPress={stopServer} color="#f44336" />
            <View style={styles.spacer} />
            <Button title="Open" onPress={() => Linking.openURL(serverUrl)} color="#4CAF50" />
          </View>
        </View>
      ) : (
        <View style={styles.stoppedContainer}>
          {isLoading ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color="#2196F3" />
              <Text style={styles.loadingText}>Starting server...</Text>
            </View>
          ) : (
            <Button 
              title="Start Server" 
              onPress={startServer} 
              color="#2196F3"
            />
          )}
        </View>
      )}

      <View style={styles.infoContainer}>
        <Text style={styles.infoTitle}>How it works</Text>
        <Text style={styles.infoText}>
          • Accessible via WiFi IP: {wifiIp}{'\n'}
          • Other devices use: {wifiIp}:{'xxxx'}{'\n'}
          • Files served from app directory{'\n'}
          • Works on same WiFi network
        </Text>
      </View>

      <View style={styles.logsContainer}>
        <Text style={styles.logsTitle}>Logs</Text>
        <ScrollView style={styles.logs}>
          {logs.map((log, i) => (
            <Text key={i} style={styles.logText}>{log}</Text>
          ))}
        </ScrollView>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
  },
  statusContainer: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
    marginBottom: 20,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  label: {
    fontSize: 16,
    color: '#666',
    fontWeight: '600',
  },
  indicator: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  indicatorText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 12,
  },
  ipText: {
    fontSize: 16,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    fontWeight: 'bold',
    color: '#333',
  },
  runningContainer: {
    backgroundColor: '#e8f5e9',
    padding: 20,
    borderRadius: 10,
    marginBottom: 20,
    alignItems: 'center',
  },
  stoppedContainer: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 10,
    marginBottom: 20,
    alignItems: 'center',
    minHeight: 80,
    justifyContent: 'center',
  },
  loadingContainer: {
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 10,
    color: '#666',
  },
  urlLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 5,
  },
  urlText: {
    fontSize: 18,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    backgroundColor: '#2c3e50',
    color: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    textAlign: 'center',
    fontWeight: 'bold',
  },
  instruction: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'center',
  },
  spacer: {
    width: 20,
  },
  infoContainer: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 10,
    marginBottom: 20,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333',
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  logsContainer: {
    marginBottom: 20,
  },
  logsTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333',
  },
  logs: {
    backgroundColor: '#000',
    borderRadius: 5,
    padding: 10,
    maxHeight: 150,
  },
  logText: {
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    fontSize: 12,
    color: '#0f0',
    marginBottom: 2,
  },
});

export default ServerComponent;