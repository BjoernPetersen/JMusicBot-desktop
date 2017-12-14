/*
 * JMusicBot
 * No description provided (generated by Swagger Codegen https://github.com/swagger-api/swagger-codegen)
 *
 * OpenAPI spec version: 0.8.0
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

/**
 * LoginCredentials
 */

public class LoginCredentials {

  @JsonProperty("name")
  private String name = null;

  @JsonProperty("password")
  private String password = null;

  @JsonProperty("uuid")
  private String uuid = null;

  public LoginCredentials name(String name) {
    this.name = name;
    return this;
  }

  /**
   * The user name.
   *
   * @return name
   **/
  @JsonProperty("name")
  @ApiModelProperty(required = true, value = "The user name.")
  @NotNull
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LoginCredentials password(String password) {
    this.password = password;
    return this;
  }

  /**
   * The user's password if he is a full user.
   *
   * @return password
   **/
  @JsonProperty("password")
  @ApiModelProperty(value = "The user's password if he is a full user.")
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public LoginCredentials uuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  /**
   * The user's UUID if he is a guest.
   *
   * @return uuid
   **/
  @JsonProperty("uuid")
  @ApiModelProperty(value = "The user's UUID if he is a guest.")
  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LoginCredentials loginCredentials = (LoginCredentials) o;
    return Objects.equals(this.name, loginCredentials.name) &&
        Objects.equals(this.password, loginCredentials.password) &&
        Objects.equals(this.uuid, loginCredentials.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, password, uuid);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class LoginCredentials {\n");

    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    uuid: ").append(toIndentedString(uuid)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces (except the first
   * line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
