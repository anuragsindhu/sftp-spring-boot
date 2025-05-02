package com.example.sftp.autoconfiguration.filters;

import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SftpFileSizeFilterTest {

  @Test
  void shouldRejectNullEntry() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);
    assertThat(filter.accept(null)).isFalse();
  }

  @Test
  void shouldRejectFileBelowMinSize() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);

    SftpClient.DirEntry entry = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributes = mock(SftpClient.Attributes.class);
    when(entry.getAttributes()).thenReturn(attributes);
    when(attributes.getSize()).thenReturn(50L);

    assertThat(filter.accept(entry)).isFalse();
  }

  @Test
  void shouldRejectFileAboveMaxSize() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);

    SftpClient.DirEntry entry = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributes = mock(SftpClient.Attributes.class);
    when(entry.getAttributes()).thenReturn(attributes);
    when(attributes.getSize()).thenReturn(1500L);

    assertThat(filter.accept(entry)).isFalse();
  }

  @Test
  void shouldAcceptFileAtMinSize() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);

    SftpClient.DirEntry entry = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributes = mock(SftpClient.Attributes.class);
    when(entry.getAttributes()).thenReturn(attributes);
    when(attributes.getSize()).thenReturn(100L);

    assertThat(filter.accept(entry)).isTrue();
  }

  @Test
  void shouldAcceptFileAtMaxSize() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);

    SftpClient.DirEntry entry = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributes = mock(SftpClient.Attributes.class);
    when(entry.getAttributes()).thenReturn(attributes);
    when(attributes.getSize()).thenReturn(1000L);

    assertThat(filter.accept(entry)).isTrue();
  }

  @Test
  void shouldAcceptFileWithinRange() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(100L, 1000L);

    SftpClient.DirEntry entry = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributes = mock(SftpClient.Attributes.class);
    when(entry.getAttributes()).thenReturn(attributes);
    when(attributes.getSize()).thenReturn(550L);

    assertThat(filter.accept(entry)).isTrue();
  }

  @Test
  void shouldWorkWithOnlyMinSpecified() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(200L, null);

    SftpClient.DirEntry entryTooSmall = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributesTooSmall = mock(SftpClient.Attributes.class);
    when(entryTooSmall.getAttributes()).thenReturn(attributesTooSmall);
    when(attributesTooSmall.getSize()).thenReturn(150L);

    SftpClient.DirEntry entryOk = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributesOk = mock(SftpClient.Attributes.class);
    when(entryOk.getAttributes()).thenReturn(attributesOk);
    when(attributesOk.getSize()).thenReturn(300L);

    assertThat(filter.accept(entryTooSmall)).isFalse();
    assertThat(filter.accept(entryOk)).isTrue();
  }

  @Test
  void shouldWorkWithOnlyMaxSpecified() {
    SftpFileSizeFilter filter = new SftpFileSizeFilter(null, 500L);

    SftpClient.DirEntry entryTooLarge = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributesTooLarge = mock(SftpClient.Attributes.class);
    when(entryTooLarge.getAttributes()).thenReturn(attributesTooLarge);
    when(attributesTooLarge.getSize()).thenReturn(600L);

    SftpClient.DirEntry entryOk = mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attributesOk = mock(SftpClient.Attributes.class);
    when(entryOk.getAttributes()).thenReturn(attributesOk);
    when(attributesOk.getSize()).thenReturn(400L);

    assertThat(filter.accept(entryTooLarge)).isFalse();
    assertThat(filter.accept(entryOk)).isTrue();
  }
}
