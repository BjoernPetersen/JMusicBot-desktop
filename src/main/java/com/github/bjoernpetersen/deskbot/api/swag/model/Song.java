/*
 * JMusicBot
 * No description provided (generated by Swagger Codegen https://github.com/swagger-api/swagger-codegen)
 *
 * OpenAPI spec version: 0.3.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package com.github.bjoernpetersen.deskbot.api.swag.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Song
 */

public class Song {

  @JsonProperty("id")
  private String id = null;

  @JsonProperty("providerId")
  private String providerId = null;

  @JsonProperty("title")
  private String title = null;

  @JsonProperty("description")
  private String description = null;

  @JsonProperty("albumArtUrl")
  private String albumArtUrl = null;

  public Song id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   *
   * @return id
   **/
  @JsonProperty("id")
  @ApiModelProperty(required = true, value = "")
  @NotNull
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Song providerId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  /**
   * Get providerId
   *
   * @return providerId
   **/
  @JsonProperty("providerId")
  @ApiModelProperty(example = "spotify", required = true, value = "")
  @NotNull
  public String getProviderId() {
    return providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public Song title(String title) {
    this.title = title;
    return this;
  }

  /**
   * The title of a song, most important identifier for human readers
   *
   * @return title
   **/
  @JsonProperty("title")
  @ApiModelProperty(example = "Never gonna give you up", required = true, value = "The title of a song, most important identifier for human readers")
  @NotNull
  @Size(min = 1)
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Song description(String description) {
    this.description = description;
    return this;
  }

  /**
   * The description of a song. Might be the artist. May also be empty.
   *
   * @return description
   **/
  @JsonProperty("description")
  @ApiModelProperty(example = "Rick Astley", required = true, value = "The description of a song. Might be the artist. May also be empty.")
  @NotNull
  @Size(min = 0)
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Song albumArtUrl(String albumArtUrl) {
    this.albumArtUrl = albumArtUrl;
    return this;
  }

  /**
   * The URL pointing to an album art for this song
   *
   * @return albumArtUrl
   **/
  @JsonProperty("albumArtUrl")
  @ApiModelProperty(example = "http://example.com/image.png", value = "The URL pointing to an album art for this song")
  public String getAlbumArtUrl() {
    return albumArtUrl;
  }

  public void setAlbumArtUrl(String albumArtUrl) {
    this.albumArtUrl = albumArtUrl;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Song song = (Song) o;
    return Objects.equals(this.id, song.id) &&
        Objects.equals(this.providerId, song.providerId) &&
        Objects.equals(this.title, song.title) &&
        Objects.equals(this.description, song.description) &&
        Objects.equals(this.albumArtUrl, song.albumArtUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, providerId, title, description, albumArtUrl);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Song {\n");

    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    providerId: ").append(toIndentedString(providerId)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    albumArtUrl: ").append(toIndentedString(albumArtUrl)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

