package com.project.model.entity;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
public class Diary {
    
    @Id
    @Column(name = "diary_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long diaryId;
    
    @Column(name = "diary_content")
    private String diaryContent;
    
    @OneToMany(mappedBy = "diary")
    private List<DiaryEmotion> diaryEmotions = new ArrayList<>();
    
    @OneToMany(mappedBy = "diary")
    private List<DiaryMet> diaryMets = new ArrayList<>();
}
