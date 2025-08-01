package com.heredata.ncdfs.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserRelationshipResult extends GenericResult {

    @JsonProperty("ncuserMount_List")
    private List<SystemUserRelationship> userRelationships;

}
