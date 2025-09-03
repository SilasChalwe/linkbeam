import React from 'react';
import { View, StyleSheet } from 'react-native';
import StaticServerComponent from '../../components/StaticServer';

export default function TabOneScreen() {
  return (
    <View style={styles.container}>
      <StaticServerComponent />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
});