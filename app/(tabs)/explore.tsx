import { Image } from 'expo-image';
import { Platform, StyleSheet } from 'react-native';

import { Collapsible } from '@/components/Collapsible';
import ParallaxScrollView from '@/components/ParallaxScrollView';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';

export default function TabTwoScreen() {
  return (
    <ParallaxScrollView
      headerBackgroundColor={{ light: '#FFFFFF', dark: '#000000' }}
      headerImage={
        <Image
          source={require('@/assets/images/linkbeam-logo.png')}
          style={styles.logoHeader}
          contentFit="contain"
        />
      }>
      {/* Title Section */}
      <ThemedView style={styles.titleContainer}>
        <ThemedText type="title">About LinkBeam</ThemedText>
      </ThemedView>

      {/* Intro Text */}
      <ThemedText>
        <ThemedText type="defaultSemiBold">LinkBeam</ThemedText> is a fast and
        secure file-sharing app that works directly over{' '}
        <ThemedText type="defaultSemiBold">WiFi</ThemedText>, with no internet
        required. It is designed and developed by{' '}
        <ThemedText type="defaultSemiBold">The Compiler Corporation</ThemedText>, 
        a proud subsidiary of{' '}
        <ThemedText type="defaultSemiBold">NextInnoMind</ThemedText>. Our goal
        is to make file transfer between phones and desktops seamless, private,
        and lightning quick.
      </ThemedText>

      {/* Collapsibles */}
      <Collapsible title="Who We Are">
        <ThemedText>
          <ThemedText type="defaultSemiBold">
            The Compiler Corporation
          </ThemedText>{' '}
          is an innovation-driven company under{' '}
          <ThemedText type="defaultSemiBold">NextInnoMind</ThemedText>. Our
          mission is to create software that empowers people to share,
          collaborate, and connect. LinkBeam is one of our flagship projects
          that reflects this commitment.
        </ThemedText>
      </Collapsible>

      <Collapsible title="Our Vision for LinkBeam">
        <ThemedText>
          LinkBeam is built to eliminate the need for cables, slow transfers,
          and third-party servers. Whether you’re a student, entrepreneur, or
          professional, LinkBeam helps you beam files instantly across devices
          using just WiFi.
        </ThemedText>
      </Collapsible>

      <Collapsible title="Cross-Platform Support">
        <ThemedText>
          LinkBeam works across{' '}
          <ThemedText type="defaultSemiBold">Android</ThemedText>,{' '}
          <ThemedText type="defaultSemiBold">iOS</ThemedText>, and{' '}
          <ThemedText type="defaultSemiBold">desktop</ThemedText>. Share photos,
          videos, documents, or any files — all without needing internet or
          cloud services.
        </ThemedText>
      </Collapsible>

      <Collapsible title="Brand Identity">
        <ThemedText>
          The name <ThemedText type="defaultSemiBold">LinkBeam</ThemedText>{' '}
          represents a beam of connection that links devices together. It
          reflects our vision of fast, secure, and effortless file sharing,
          powered by innovation and simplicity.
        </ThemedText>
        {/* Inline Logo */}
        <Image
          source={require('@/assets/images/linkbeam-logo.png')}
          style={styles.inlineLogo}
          contentFit="contain"
        />
      </Collapsible>

      <Collapsible title="Dark & Light Mode">
        <ThemedText>
          LinkBeam adapts to your system settings with both light and dark mode
          support, ensuring a smooth and personalized user experience.
        </ThemedText>
      </Collapsible>

      <Collapsible title="Powered by Innovation">
        <ThemedText>
          Every feature inside LinkBeam is crafted for speed, security, and
          simplicity. With no internet dependency, LinkBeam ensures your files
          stay private while transfers remain lightning fast.
        </ThemedText>
        {Platform.select({
          ios: (
            <ThemedText>
              iOS users also enjoy an elegant parallax header effect for a
              polished look.
            </ThemedText>
          ),
        })}
      </Collapsible>
    </ParallaxScrollView>
  );
}

const styles = StyleSheet.create({
  logoHeader: {
    width: '100%',
    height: 200,
    alignSelf: 'center',
    marginTop: 20,
  },
  inlineLogo: {
    width: 120,
    height: 120,
    alignSelf: 'center',
    marginTop: 12,
  },
  titleContainer: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
});
